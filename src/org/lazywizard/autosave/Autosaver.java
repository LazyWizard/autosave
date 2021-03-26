package org.lazywizard.autosave;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.PlayerMarketTransaction;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.lazywizard.lazylib.JSONUtils;
import org.lazywizard.lazylib.JSONUtils.CommonDataJSONObject;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

class Autosaver extends BaseCampaignEventListener implements EveryFrameScript
{
    private static final Logger Log = Logger.getLogger(Autosaver.class);
    private static int minutesBeforeSaveWarning, minutesBetweenSaveWarnings,
            minutesBeforeForcedAutosave, saveKey;
    private static boolean autosavesEnabled, forceSaveAfterPlayerBattles,
            forceSaveAfterMarketTransactions;
    private boolean shouldAutosave = false;
    private int battlesSinceLastSave = 0, transactionsSinceLastSave = 0;
    private long lastWarn, lastSave;

    static void reloadSettings() throws IOException, JSONException
    {
        //Log.setLevel(Level.ALL);

        // Delete old settings file if present
        Global.getSettings().deleteTextFileFromCommon("config/autosave_settings.json");
        final CommonDataJSONObject settings = JSONUtils.loadCommonJSON("config/lw_autosave_settings.json", "autosave_settings.json.default");

        // Autosave settings
        minutesBeforeSaveWarning = settings.getInt(
                "minutesWithoutSaveBeforeWarning");
        minutesBetweenSaveWarnings = settings.getInt(
                "minutesBetweenSubsequentWarnings");
        autosavesEnabled = settings.getBoolean("enableAutosaves");
        saveKey = settings.optInt("saveKey", KeyEvent.VK_F5);
        minutesBeforeForcedAutosave = settings.getInt(
                "minutesBeforeForcedAutosave");
        forceSaveAfterMarketTransactions = settings.getBoolean(
                "forceSaveAfterMarketTransactions");
        forceSaveAfterPlayerBattles = settings.getBoolean(
                "forceSaveAfterPlayerBattles");

        Log.debug("Settings: " +
                "\n - enableAutosaves: " + autosavesEnabled +
                "\n - minutesWithoutSaveBeforeWarning: " + minutesBeforeSaveWarning +
                "\n - minutesBetweenSubsequentWarnings: " + minutesBetweenSaveWarnings +
                "\n - minutesBeforeForcedAutosave" + minutesBeforeForcedAutosave +
                "\n - forceSaveAfterMarketTransactions: " + forceSaveAfterMarketTransactions +
                "\n - forceSaveAfterPlayerBattles: " + forceSaveAfterPlayerBattles +
                "\n - saveKey: " + saveKey);
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
        if (autosavesEnabled && forceSaveAfterMarketTransactions)
        {
            Log.debug("Market autosave triggered, " + getMinutesSinceLastSave()
                    + " minutes since last save");
            shouldAutosave = true;
        }
        else
        {
            Log.debug("Market autosaves disabled, gnoring transaction");
        }
    }

    @Override
    public void reportPlayerEngagement(EngagementResultAPI result)
    {
        battlesSinceLastSave++;
        if (autosavesEnabled && forceSaveAfterPlayerBattles)
        {
            Log.debug("Battle autosave triggered, " + getMinutesSinceLastSave()
                    + " minutes since last save");
            shouldAutosave = true;
        }
        else
        {
            Log.debug("Market autosaves disabled, gnoring transaction");
        }
    }

    private void runChecks()
    {
        final int minutesSinceLastSave = getMinutesSinceLastSave();
        if (minutesSinceLastSave >= minutesBeforeSaveWarning
                && getMinutesSinceLastWarning() >= minutesBetweenSaveWarnings)
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

        if (autosavesEnabled && minutesSinceLastSave >= minutesBeforeForcedAutosave)
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
        if (Global.getSector().isInNewGameAdvance() || ui.isShowingDialog() || ui.isShowingMenu())
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
            Log.debug("Autosave trigger hit");
        }
    }

    void resetTimeSinceLastSave()
    {
        lastSave = lastWarn = System.currentTimeMillis();
        battlesSinceLastSave = 0;
        transactionsSinceLastSave = 0;
        Log.debug("Reset last save time");
    }

    Autosaver()
    {
        super(false);
        resetTimeSinceLastSave();
    }

    private static class AutosaveScript implements EveryFrameScript
    {
        private Robot robot = null;
        private boolean isPressed = false, isDone = false;

        private AutosaveScript()
        {
            try
            {
                robot = new Robot();
            }
            catch (AWTException ex)
            {
                // Unlikely this is a one-time failure, so disable subsequent autosaves
                autosavesEnabled = false;
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
            if (isDone || Global.getSector().isInNewGameAdvance() || ui.isShowingDialog() || ui.isShowingMenu())
            {
                return;
            }

            // Press key if not already pressed
            if (!isPressed)
            {
                Log.debug("Attempting autosave...");
                robot.keyPress(saveKey);
                isPressed = true;
            }
            // Release key one frame later
            else
            {
                Log.debug("Autosave complete!");
                robot.keyRelease(saveKey);
                isDone = true;
            }
        }
    }
}
