package org.lazywizard.autosave;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.JumpPointAPI.JumpDestination;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.JSONUtils;
import org.lazywizard.lazylib.JSONUtils.CommonDataJSONObject;

import java.awt.Color;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

class Autosaver extends BaseCampaignEventListener implements EveryFrameScript
{
    private static final String CONFIG_PATH = "config/lw_autosave_settings.json";
    private static final Logger Log = Logger.getLogger(Autosaver.class);
    private static int minutesBeforeSaveWarning,
            minutesBetweenSaveWarnings,
            minutesBeforeForcedAutosave;
    private static boolean autosavesEnabled,
            forceSaveAfterPlayerBattles,
            forceSaveAfterMarketTransactions,
            forceSaveAfterMapChange,
            saveAsCopy;
    private boolean shouldAutosave = false;
    private int battlesSinceLastSave = 0,
            transactionsSinceLastSave = 0;
    private long lastWarn, lastSave;

    private static boolean optBoolean(JSONObject settings, String key, boolean defaultBoolean) throws JSONException
    {
        final boolean setting = settings.optBoolean(key, defaultBoolean);
        settings.put(key, setting);
        return setting;
    }

    private static int optInt(JSONObject settings, String key, int defaultInt) throws JSONException
    {
        final int setting = settings.optInt(key, defaultInt);
        settings.put(key, setting);
        return setting;
    }

    static void reloadSettings() throws IOException, JSONException
    {
        //Log.setLevel(Level.ALL);

        // Delete old settings file if present
        Global.getSettings().deleteTextFileFromCommon("config/autosave_settings.json");
        final CommonDataJSONObject settings = JSONUtils.loadCommonJSON(CONFIG_PATH);

        // Autosave settings
        minutesBeforeSaveWarning = optInt(settings,
                "minutesWithoutSaveBeforeWarning", 15);
        minutesBetweenSaveWarnings = optInt(settings,
                "minutesBetweenSubsequentWarnings", 5);
        autosavesEnabled = optBoolean(settings,"enableAutosaves", false);
        saveAsCopy = optBoolean(settings,"saveAsCopy", false);
        minutesBeforeForcedAutosave = optInt(settings,
                "minutesBeforeForcedAutosave", 30);
        forceSaveAfterMarketTransactions = optBoolean(settings,
                "forceSaveAfterMarketTransactions", false);
        forceSaveAfterPlayerBattles = optBoolean(settings,
                "forceSaveAfterPlayerBattles", false);
        forceSaveAfterMapChange = optBoolean(settings,
                "forceSaveAfterMapChange", false);
        settings.save();

        Log.debug("Settings: " +
                "\n - enableAutosaves: " + autosavesEnabled +
                "\n - saveAsCopy: " + saveAsCopy +
                "\n - minutesWithoutSaveBeforeWarning: " + minutesBeforeSaveWarning +
                "\n - minutesBetweenSubsequentWarnings: " + minutesBetweenSaveWarnings +
                "\n - minutesBeforeForcedAutosave" + minutesBeforeForcedAutosave +
                "\n - forceSaveAfterMarketTransactions: " + forceSaveAfterMarketTransactions +
                "\n - forceSaveAfterPlayerBattles: " + forceSaveAfterPlayerBattles +
                "\n - forceSaveAfterMapChange: " + forceSaveAfterMapChange);
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
            Log.debug("Market autosaves disabled, ignoring transaction");
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
            Log.debug("Market autosaves disabled, ignoring transaction");
        }
    }

    @Override
    public void reportFleetJumped(CampaignFleetAPI fleet, SectorEntityToken from, JumpDestination to)
    {
        if (autosavesEnabled && forceSaveAfterMapChange && fleet.isPlayerFleet() && from != to.getDestination())
        {
            Log.debug("Map change autosave triggered, " + getMinutesSinceLastSave()
                    + " minutes since last save");
            shouldAutosave = true;
        }
        else
        {
            Log.debug("Map change autosaves disabled, ignoring jump");
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

            Log.debug("Beginning autosave");
            if (saveAsCopy)
            {
                Global.getSector().getCampaignUI().cmdSaveCopy();
            }
            else
            {
                Global.getSector().getCampaignUI().cmdSave();
            }

            Log.debug("Autosave complete");
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
}
