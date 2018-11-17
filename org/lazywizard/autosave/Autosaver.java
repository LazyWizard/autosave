package org.lazywizard.autosave;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.PlayerMarketTransaction;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.lazywizard.lazylib.JSONUtils;
import org.lazywizard.lazylib.JSONUtils.CommonDataJSONObject;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

class Autosaver extends BaseCampaignEventListener implements EveryFrameScript
{
    private static final Logger Log = Logger.getLogger(Autosaver.class);
    private static int MINUTES_BEFORE_SAVE_WARNING, MINUTES_BETWEEN_SAVE_WARNINGS,
            MINUTES_BEFORE_FORCED_AUTOSAVE, SAVE_KEY;
    private static boolean AUTOSAVES_ENABLED, FORCE_SAVE_AFTER_PLAYER_BATTLES,
            FORCE_SAVE_AFTER_MARKET_TRANSACTIONS;
    private boolean shouldAutosave = false;
    private int battlesSinceLastSave = 0, transactionsSinceLastSave = 0;
    private long lastWarn, lastSave;

    static void reloadSettings() throws IOException, JSONException
    {
        final CommonDataJSONObject settings = JSONUtils.loadCommonJSON("autosave_settings.json", "autosave_settings.json.default");

        // Autosave settings
        MINUTES_BEFORE_SAVE_WARNING = settings.getInt(
                "minutesWithoutSaveBeforeWarning");
        MINUTES_BETWEEN_SAVE_WARNINGS = settings.getInt(
                "minutesBetweenSubsequentWarnings");
        AUTOSAVES_ENABLED = settings.getBoolean("enableAutosaves");
        SAVE_KEY = settings.optInt("saveKey", KeyEvent.VK_F5);
        MINUTES_BEFORE_FORCED_AUTOSAVE = settings.getInt(
                "minutesBeforeForcedAutosave");
        FORCE_SAVE_AFTER_MARKET_TRANSACTIONS = settings.getBoolean(
                "forceSaveAfterMarketTransactions");
        FORCE_SAVE_AFTER_PLAYER_BATTLES = settings.getBoolean(
                "forceSaveAfterPlayerBattles");
    }

    private void forceSave()
    {
        Global.getSector().addTransientScript(new AutosaveScript());
    }

    private int getMinutesSinceLastSave()
    {
        return (int) TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - lastSave);
    }

    private int getMinutesSinceLastWarning()
    {
        return (int) TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - lastWarn);
    }

    @Override
    public void reportPlayerMarketTransaction(PlayerMarketTransaction transaction)
    {
        transactionsSinceLastSave++;
        if (AUTOSAVES_ENABLED && FORCE_SAVE_AFTER_MARKET_TRANSACTIONS)
        {
            Log.debug("Market autosave triggered, " + getMinutesSinceLastSave()
                    + " minutes since last save");
            shouldAutosave = true;
        }
    }

    @Override
    public void reportPlayerEngagement(EngagementResultAPI result)
    {
        battlesSinceLastSave++;
        if (AUTOSAVES_ENABLED && FORCE_SAVE_AFTER_PLAYER_BATTLES)
        {
            Log.debug("Battle autosave triggered, " + getMinutesSinceLastSave()
                    + " minutes since last save");
            shouldAutosave = true;
        }
    }

    private void runChecks()
    {
        final int minutesSinceLastSave = getMinutesSinceLastSave();
        if (minutesSinceLastSave >= MINUTES_BEFORE_SAVE_WARNING
                && getMinutesSinceLastWarning() >= MINUTES_BETWEEN_SAVE_WARNINGS)
        {
            Global.getSector().getCampaignUI().addMessage(
                    "It has been " + minutesSinceLastSave + " minutes since"
                            + " your last save.", Color.YELLOW);
            Global.getSector().getCampaignUI().addMessage(battlesSinceLastSave
                            + " player battles and " + transactionsSinceLastSave
                            + " market transactions have occurred in this time.",
                    Color.YELLOW);
            lastWarn = System.currentTimeMillis();
        }

        if (AUTOSAVES_ENABLED && minutesSinceLastSave >= MINUTES_BEFORE_FORCED_AUTOSAVE)
        {
            Global.getSector().getCampaignUI().addMessage("Autosaving...", Color.YELLOW);
            shouldAutosave = true;
        }
    }

    @Override
    public boolean isDone()
    {
        return false;
    }

    @Override
    public boolean runWhilePaused()
    {
        return true;
    }

    @Override
    public void advance(float amount)
    {
        // Can't save while in a menu
        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        if (Global.getSector().isInNewGameAdvance() || ui.isShowingDialog())
        {
            return;
        }

        // Check if it's been long enough since the last save to warn the player
        // Explicitly autosave if the player has been wandering aimlessly long enough
        runChecks();

        // If we've hit an autosave trigger, force a save key event
        if (shouldAutosave)
        {
            shouldAutosave = false;
            resetTimeSinceLastSave();
            forceSave();
        }
    }

    void resetTimeSinceLastSave()
    {
        lastSave = lastWarn = System.currentTimeMillis();
        battlesSinceLastSave = 0;
        transactionsSinceLastSave = 0;
    }

    Autosaver()
    {
        super(false);
        resetTimeSinceLastSave();
    }

    private class AutosaveScript implements EveryFrameScript
    {
        private Robot robot = null;
        private boolean isPressed = false, isDone = false;
        private float timePressed = 0f;

        private AutosaveScript()
        {
            try
            {
                robot = new Robot();
            }
            catch (AWTException ex)
            {
                // Unlikely this is a one-time failure, so disable subsequent autosaves
                AUTOSAVES_ENABLED = false;
                isDone = true;
                Log.error("Failed to autosave: ", ex);
            }
        }

        @Override
        public boolean isDone()
        {
            return isDone;
        }

        @Override
        public boolean runWhilePaused()
        {
            return true;
        }

        @Override
        public void advance(float amount)
        {
            // Can't save while in a menu
            CampaignUIAPI ui = Global.getSector().getCampaignUI();
            if (isDone || Global.getSector().isInNewGameAdvance() || ui.isShowingDialog())
            {
                return;
            }

            // Press key if not already pressed
            if (!isPressed)
            {
                Log.debug("Attempting autosave...");
                robot.keyPress(SAVE_KEY);
                isPressed = true;
            }
            // Release key one frame later
            else
            {
                robot.keyRelease(SAVE_KEY);
                isDone = true;
            }
        }
    }
}
