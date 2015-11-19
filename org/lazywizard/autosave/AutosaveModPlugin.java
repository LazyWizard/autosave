package org.lazywizard.autosave;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;

public class AutosaveModPlugin extends BaseModPlugin
{
    private static Autosaver saver;

    @Override
    public void onApplicationLoad() throws Exception
    {
        Autosaver.reloadSettings();
        saver = new Autosaver();
    }

    @Override
    public void onGameLoad(boolean newGame)
    {
        saver.resetTimeSinceLastSave();
        Global.getSector().addTransientScript(saver);
        Global.getSector().addTransientListener(saver);
    }

    @Override
    public void afterGameSave()
    {
        saver.resetTimeSinceLastSave();
    }
}
