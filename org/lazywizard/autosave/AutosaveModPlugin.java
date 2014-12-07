package org.lazywizard.autosave;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;

/**
 * @author LazyWizard
 */
public class AutosaveModPlugin extends BaseModPlugin
{
    private Autosaver saver;

    @Override
    public void onApplicationLoad() throws Exception
    {
        Autosaver.reloadSettings();
        if (!Autosaver.ENABLE_AUTOSAVES)
        {
            return;
        }

        saver = new Autosaver();
    }

    @Override
    public void onGameLoad()
    {
        if (!Autosaver.ENABLE_AUTOSAVES)
        {
            return;
        }

        saver.resetAutosaveTimer();
        Global.getSector().addTransientScript(saver);
        Global.getSector().addTransientListener(saver);
    }

    @Override
    public void afterGameSave()
    {
        if (!Autosaver.ENABLE_AUTOSAVES)
        {
            return;
        }

        // Reset timer after a manual save
        saver.resetAutosaveTimer();
    }
}
