package org.lazywizard.autosave;

import java.awt.AWTException;
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
    static boolean ENABLE_AUTOSAVES;
    private static int SAVE_KEY;
    private static int TIME_BETWEEN_FORCED_AUTOSAVES;
    private static int MIN_TIME_BETWEEN_MARKET_AUTOSAVES;
    private static int MIN_TIME_BETWEEN_BATTLE_AUTOSAVES;
    private long lastAutosave;
    private boolean needsAutosave = false;

    static void reloadSettings() throws IOException, JSONException
    {
        JSONObject settings = Global.getSettings().loadJSON("autosave_settings.json");
        ENABLE_AUTOSAVES = settings.optBoolean("enabled", false);
        SAVE_KEY = settings.optInt("saveKey", KeyEvent.VK_F5);
        TIME_BETWEEN_FORCED_AUTOSAVES = settings.optInt(
                "timeBetweenForcedAutosave", 300);
        MIN_TIME_BETWEEN_MARKET_AUTOSAVES = settings.optInt(
                "minTimeBetweenMarketAutosaves", 120);
        MIN_TIME_BETWEEN_BATTLE_AUTOSAVES = settings.optInt(
                "minTimeBetweenBattleAutosaves", 60);
    }

    private void forceSave()
    {
        try
        {
            Global.getLogger(Autosaver.class).log(Level.DEBUG,
                    "Attempting autosave...");
            Robot robot = new Robot();
            robot.keyPress(SAVE_KEY);
        }
        catch (AWTException ex)
        {
            // Disable autosaves
            ENABLE_AUTOSAVES = false;
            Global.getLogger(Autosaver.class).log(Level.ERROR,
                    "Failed to autosave: ", ex);
        }
    }

    private float getTimeSinceLastAutosave()
    {
        return TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - lastAutosave);
    }

    @Override
    public void reportPlayerMarketTransaction(PlayerMarketTransaction transaction)
    {
        if (getTimeSinceLastAutosave() >= MIN_TIME_BETWEEN_MARKET_AUTOSAVES)
        {
            Global.getLogger(Autosaver.class).log(Level.DEBUG,
                    "It has been " + getTimeSinceLastAutosave()
                    + " seconds since last autosave, market autosave triggered");
            needsAutosave = true;
        }
    }

    @Override
    public void reportPlayerEngagement(EngagementResultAPI result)
    {
        if (getTimeSinceLastAutosave() >= MIN_TIME_BETWEEN_BATTLE_AUTOSAVES)
        {
            Global.getLogger(Autosaver.class).log(Level.DEBUG,
                    "It has been " + getTimeSinceLastAutosave()
                    + " seconds since last autosave, battle autosave triggered");
            needsAutosave = true;
        }
    }

    private void checkForceAutosave()
    {
        if (getTimeSinceLastAutosave() >= TIME_BETWEEN_FORCED_AUTOSAVES)
        {
            Global.getLogger(Autosaver.class).log(Level.DEBUG,
                    "It has been " + getTimeSinceLastAutosave()
                    + " seconds since last autosave, forced autosave triggered");
            needsAutosave = true;
        }
    }

    @Override
    public boolean isDone()
    {
        return !ENABLE_AUTOSAVES;
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

        // Explicitly autosave if the player has been wandering aimlessly long enough
        checkForceAutosave();

        // If we've hit an autosave trigger, force a save key event
        if (needsAutosave)
        {
            needsAutosave = false;
            forceSave();
        }
    }

    void resetAutosaveTimer()
    {
        lastAutosave = System.currentTimeMillis();
    }

    Autosaver()
    {
        super(false);
        resetAutosaveTimer();
    }
}
