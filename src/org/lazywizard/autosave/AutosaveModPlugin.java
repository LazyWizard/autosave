package org.lazywizard.autosave;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import sun.rmi.runtime.Log;

public class AutosaveModPlugin extends BaseModPlugin
{
    private static final Logger Log = Global.getLogger(AutosaveModPlugin.class);
    private static Autosaver saver;

    @Override
    public void onApplicationLoad() throws Exception
    {
        //Log.setLevel(Level.ALL);
        Log.debug("Loading settings");
        Autosaver.reloadSettings();
        saver = new Autosaver();
        Log.debug("Settings loaded");
    }

    @Override
    public void onGameLoad(boolean newGame)
    {
        saver.resetTimeSinceLastSave();
        Global.getSector().addTransientScript(saver);
        Global.getSector().addTransientListener(saver);
        Log.debug("Added autosaver to sector");
    }

    @Override
    public void afterGameSave()
    {
        saver.resetTimeSinceLastSave();
    }
}
