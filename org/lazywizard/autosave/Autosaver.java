package org.lazywizard.autosave;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.PlayerMarketTransaction;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import org.apache.log4j.Level;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * @author LazyWizard
 */
class Autosaver extends BaseCampaignEventListener implements EveryFrameScript
{
    private static boolean AUTOSAVES_ENABLED;
    private static int SAVE_KEY;
    private static int MINUTES_BEFORE_SAVE_WARNING, MINUTES_BEFORE_FORCED_AUTOSAVE;
    private static boolean FORCE_SAVE_AFTER_MARKET_TRANSACTIONS,
            FORCE_SAVE_AFTER_PLAYER_BATTLES;
    private boolean needsAutosave = false, hasWarned = false;
    private int battlesSinceLastSave = 0, transactionsSinceLastSave = 0;
    private long lastSave;

    static void reloadSettings() throws IOException, JSONException
    {
        JSONObject settings = Global.getSettings().loadJSON("autosave_settings.json");

        // Autosave settings
        MINUTES_BEFORE_SAVE_WARNING = settings.getInt(
                "minutesWithoutSaveBeforeWarning");
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
        try
        {
            Global.getLogger(Autosaver.class).log(Level.DEBUG,
                    "Attempting autosave...");
            new Robot().keyPress(SAVE_KEY);
        }
        catch (AWTException ex)
        {
            // Disable autosaves
            AUTOSAVES_ENABLED = false;
            Global.getLogger(Autosaver.class).log(Level.ERROR,
                    "Failed to autosave: ", ex);
        }
    }

    private int getMinutesSinceLastSave()
    {
        return (int) TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - lastSave);
    }

    static boolean areAutosavesEnabled()
    {
        return AUTOSAVES_ENABLED;
    }

    @Override
    public void reportPlayerMarketTransaction(PlayerMarketTransaction transaction)
    {
        transactionsSinceLastSave++;
        if (AUTOSAVES_ENABLED && FORCE_SAVE_AFTER_MARKET_TRANSACTIONS)
        {
            Global.getLogger(Autosaver.class).log(Level.DEBUG,
                    "Market autosave triggered, " + getMinutesSinceLastSave()
                    + " minutes since last save");
            needsAutosave = true;
        }
    }

    @Override
    public void reportPlayerEngagement(EngagementResultAPI result)
    {
        battlesSinceLastSave++;
        if (AUTOSAVES_ENABLED && FORCE_SAVE_AFTER_PLAYER_BATTLES)
        {
            Global.getLogger(Autosaver.class).log(Level.DEBUG,
                    "Battle autosave triggered, " + getMinutesSinceLastSave()
                    + " minutes since last save");
            needsAutosave = true;
        }
    }

    private void checkTimeSinceLastSave()
    {
        if (hasWarned && !AUTOSAVES_ENABLED)
        {
            return;
        }

        final int minutesSinceLastSave = getMinutesSinceLastSave();
        if (!hasWarned && minutesSinceLastSave >= MINUTES_BEFORE_SAVE_WARNING)
        {
            Global.getSector().getCampaignUI().addMessage(
                    "It has been " + minutesSinceLastSave + " minutes since"
                    + " your last save.", Color.YELLOW);
            Global.getSector().getCampaignUI().addMessage(battlesSinceLastSave
                    + " player battles and " + transactionsSinceLastSave
                    + " market transactions have occured in this time.",
                    Color.YELLOW);
            hasWarned = true;
        }

        if (AUTOSAVES_ENABLED && minutesSinceLastSave >= MINUTES_BEFORE_FORCED_AUTOSAVE)
        {
            Global.getSector().getCampaignUI().addMessage("It has been "
                    + minutesSinceLastSave + " minutes since your last"
                    + " save, forced autosave triggered.", Color.YELLOW);
            needsAutosave = true;
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
        checkTimeSinceLastSave();

        // If we've hit an autosave trigger, force a save key event
        if (needsAutosave)
        {
            needsAutosave = false;
            forceSave();
        }
    }

    void resetTimeSinceLastSave()
    {
        lastSave = System.currentTimeMillis();
        battlesSinceLastSave = 0;
        transactionsSinceLastSave = 0;
        hasWarned = false;
    }

    Autosaver()
    {
        super(false);
        resetTimeSinceLastSave();
    }
}
