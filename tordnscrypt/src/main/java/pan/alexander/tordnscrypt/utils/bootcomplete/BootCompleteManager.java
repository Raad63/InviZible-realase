/*
    This file is part of InviZible Pro.

    InviZible Pro is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    InviZible Pro is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with InviZible Pro.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2019-2025 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.utils.bootcomplete;

import static pan.alexander.tordnscrypt.BootCompleteReceiver.MY_PACKAGE_REPLACED;
import static pan.alexander.tordnscrypt.di.SharedPreferencesModule.DEFAULT_PREFERENCES_NAME;
import static pan.alexander.tordnscrypt.modules.ModulesServiceActions.ACTION_STOP_SERVICE_FOREGROUND;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STARTING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPED;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.ROOT_MODE;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.UNDEFINED;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.VPN_MODE;
import static pan.alexander.tordnscrypt.utils.logger.Logger.loge;
import static pan.alexander.tordnscrypt.utils.logger.Logger.logi;
import static pan.alexander.tordnscrypt.utils.logger.Logger.logw;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.FIX_TTL;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.OPERATION_MODE;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.PREVENT_DNS_LEAKS;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.REMOTE_CONTROL;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.ROOT_IS_AVAILABLE;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.RUN_MODULES_WITH_ROOT;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.SAVED_DNSCRYPT_STATE_PREF;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.SAVED_ITPD_STATE_PREF;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.SAVED_TOR_STATE_PREF;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.VPN_SERVICE_ENABLED;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Build;
import android.os.Handler;

import javax.inject.Inject;
import javax.inject.Named;

import dagger.Lazy;
import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository;
import pan.alexander.tordnscrypt.modules.ModulesActionSender;
import pan.alexander.tordnscrypt.modules.ModulesAux;
import pan.alexander.tordnscrypt.modules.ModulesKiller;
import pan.alexander.tordnscrypt.modules.ModulesRunner;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.modules.ModulesStatusBroadcaster;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.ap.ApManager;
import pan.alexander.tordnscrypt.utils.enums.ModuleState;
import pan.alexander.tordnscrypt.utils.enums.OperationMode;
import pan.alexander.tordnscrypt.utils.filemanager.FileShortener;
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys;
import pan.alexander.tordnscrypt.utils.workers.UpdateIPsManager;
import pan.alexander.tordnscrypt.vpn.service.ServiceVPNHelper;

public class BootCompleteManager {

    public static final String ALWAYS_ON_VPN = "pan.alexander.tordnscrypt.ALWAYS_ON_VPN";
    public static final String SHELL_SCRIPT_CONTROL = "pan.alexander.tordnscrypt.SHELL_SCRIPT_CONTROL";

    public static final String MANAGE_TOR_EXTRA = "tor";
    public static final String MANAGE_DNSCRYPT_EXTRA = "dnscrypt";
    public static final String MANAGE_ITPD_EXTRA = "i2p";

    private final Lazy<PreferenceRepository> preferenceRepository;
    private final Lazy<SharedPreferences> defaultSharedPreferences;
    private final Lazy<Handler> handler;
    private final Lazy<PathVars> pathVars;
    private final Lazy<ApManager> apManager;
    private final Lazy<ModulesStatusBroadcaster> modulesStatusBroadcaster;
    private final Lazy<UpdateIPsManager> updateIPsManager;
    private Context context;
    private String appDataDir;

    @Inject
    public BootCompleteManager(
            @Named(DEFAULT_PREFERENCES_NAME)
            Lazy<SharedPreferences> defaultSharedPreferences,
            Lazy<PreferenceRepository> preferenceRepository,
            Lazy<Handler> handler,
            Lazy<PathVars> pathVars,
            Lazy<ApManager> apManager,
            Lazy<ModulesStatusBroadcaster> modulesStatusBroadcaster,
            Lazy<UpdateIPsManager> updateIPsManager
    ) {
        this.defaultSharedPreferences = defaultSharedPreferences;
        this.preferenceRepository = preferenceRepository;
        this.handler = handler;
        this.pathVars = pathVars;
        this.apManager = apManager;
        this.modulesStatusBroadcaster = modulesStatusBroadcaster;
        this.updateIPsManager = updateIPsManager;
    }

    private final ModulesStatus modulesStatus = ModulesStatus.getInstance();

    public void performAction(final Context context, Intent intent) {

        this.context = context;

        appDataDir = pathVars.get().getAppDataDir();

        SharedPreferences defaultPreferences = defaultSharedPreferences.get();
        PreferenceRepository preferences = preferenceRepository.get();

        String action = intent.getAction();

        logi("Boot complete manager receive " + action);

        if (action == null) {
            return;
        }

        if (action.equals(SHELL_SCRIPT_CONTROL) && !defaultPreferences.getBoolean(REMOTE_CONTROL, false)) {
            broadcastControlDisabled();
            return;
        }

        preferences.setBoolPreference(PreferenceKeys.WIFI_ACCESS_POINT_IS_ON, false);
        preferences.setBoolPreference(PreferenceKeys.USB_MODEM_IS_ON, false);

        boolean tethering_autostart = defaultPreferences.getBoolean("pref_common_tethering_autostart", false);

        boolean rootIsAvailable = preferences.getBoolPreference(ROOT_IS_AVAILABLE);
        boolean runModulesWithRoot = defaultPreferences.getBoolean(RUN_MODULES_WITH_ROOT, false);
        boolean fixTTL = defaultPreferences.getBoolean(FIX_TTL, false);
        String operationMode = preferences.getStringPreference(OPERATION_MODE);

        OperationMode mode = UNDEFINED;
        if (!operationMode.isEmpty()) {
            mode = OperationMode.valueOf(operationMode);
        }

        ModulesAux.switchModes(rootIsAvailable, runModulesWithRoot, mode);

        boolean autoStartDNSCrypt = defaultPreferences.getBoolean("swAutostartDNS", false);
        boolean autoStartTor = defaultPreferences.getBoolean("swAutostartTor", false);
        boolean autoStartITPD = defaultPreferences.getBoolean("swAutostartITPD", false);

        boolean savedDNSCryptStateRunning = ModulesAux.isDnsCryptSavedStateRunning();
        boolean savedTorStateRunning = ModulesAux.isTorSavedStateRunning();
        boolean savedITPDStateRunning = ModulesAux.isITPDSavedStateRunning();
        boolean savedFirewallStateRunning = ModulesAux.isFirewallSavedStateRunning();

        boolean autoStartFirewall = autoStartDNSCrypt || autoStartTor
                || autoStartITPD && savedFirewallStateRunning;

        if (action.equalsIgnoreCase(MY_PACKAGE_REPLACED) || action.equalsIgnoreCase(ALWAYS_ON_VPN)) {
            autoStartDNSCrypt = savedDNSCryptStateRunning;
            autoStartTor = savedTorStateRunning;
            autoStartITPD = savedITPDStateRunning;
            autoStartFirewall = autoStartDNSCrypt || autoStartTor || savedFirewallStateRunning;
        } else if (action.equals(SHELL_SCRIPT_CONTROL)) {
            int startDnsCrypt = intent.getIntExtra(MANAGE_DNSCRYPT_EXTRA, -1);
            int startTor = intent.getIntExtra(MANAGE_TOR_EXTRA, -1);
            int startItpd = intent.getIntExtra(MANAGE_ITPD_EXTRA, -1);

            if (startDnsCrypt < 0) {
                autoStartDNSCrypt = savedDNSCryptStateRunning;
            } else {
                autoStartDNSCrypt = startDnsCrypt == 1;
                broadcastDNSCryptState(autoStartDNSCrypt);
            }
            if (startTor < 0) {
                autoStartTor = savedTorStateRunning;
            } else {
                autoStartTor = startTor == 1;
                broadcastTorState(autoStartTor);
            }
            if (startItpd < 0) {
                autoStartITPD = savedITPDStateRunning;
            } else {
                autoStartITPD = startItpd == 1;
                broadcastItpdState(autoStartITPD);
            }

            autoStartFirewall = autoStartDNSCrypt || autoStartTor
                    || autoStartITPD && savedFirewallStateRunning;

            logi("SHELL_SCRIPT_CONTROL start: " +
                    "DNSCrypt " + autoStartDNSCrypt + " Tor " + autoStartTor + " ITPD " + autoStartITPD);
        } else {
            resetModulesSavedState(preferences);
        }

        if (savedDNSCryptStateRunning || savedTorStateRunning || savedITPDStateRunning
                || savedFirewallStateRunning) {
            stopServicesForeground(context, mode, fixTTL);
        }

        if (autoStartITPD) {
            //shortenTooLongITPDLog();
        }

        ModulesStatus modulesStatus = ModulesStatus.getInstance();
        modulesStatus.setFixTTL(fixTTL);

        if (tethering_autostart) {

            if (!action.equalsIgnoreCase(MY_PACKAGE_REPLACED) && !action.equalsIgnoreCase(ALWAYS_ON_VPN)
                    && !action.equals(SHELL_SCRIPT_CONTROL)) {
                startHOTSPOT(preferences);
            }

        }

        if (autoStartDNSCrypt && !runModulesWithRoot
                && !defaultPreferences.getBoolean(PREVENT_DNS_LEAKS, false)
                && !action.equalsIgnoreCase(MY_PACKAGE_REPLACED)
                && !action.equals(SHELL_SCRIPT_CONTROL)) {
            modulesStatus.setSystemDNSAllowed(true);
        }

        if (autoStartDNSCrypt && autoStartTor && autoStartITPD) {
            startStopRestartModules(true, true, true, autoStartFirewall);
        } else if (autoStartDNSCrypt && autoStartTor) {
            startStopRestartModules(true, true, false, autoStartFirewall);
        } else if (autoStartDNSCrypt && !autoStartITPD) {
            startStopRestartModules(true, false, false, autoStartFirewall);
            updateIPsManager.get().stopRefreshTorUnlockIPs();
        } else if (!autoStartDNSCrypt && autoStartTor && !autoStartITPD) {
            startStopRestartModules(false, true, false, autoStartFirewall);
        } else if (!autoStartDNSCrypt && !autoStartTor && autoStartITPD) {
            startStopRestartModules(false, false, true, autoStartFirewall);
            updateIPsManager.get().stopRefreshTorUnlockIPs();
        } else if (!autoStartDNSCrypt && autoStartTor) {
            startStopRestartModules(false, true, true, autoStartFirewall);
        } else if (autoStartDNSCrypt) {
            startStopRestartModules(true, false, true, autoStartFirewall);
            updateIPsManager.get().stopRefreshTorUnlockIPs();
        } else {
            startStopRestartModules(false, false, false, autoStartFirewall);
            updateIPsManager.get().stopRefreshTorUnlockIPs();
        }

        if ((autoStartDNSCrypt || autoStartTor || autoStartITPD || autoStartFirewall)
                && (mode == VPN_MODE || fixTTL)) {
            final Intent prepareIntent = VpnService.prepare(context);

            if (prepareIntent == null) {
                handler.get().postDelayed(() -> {
                    defaultPreferences.edit().putBoolean(VPN_SERVICE_ENABLED, true).apply();

                    String reason = switch (action) {
                        case MY_PACKAGE_REPLACED -> "MY_PACKAGE_REPLACED";
                        case ALWAYS_ON_VPN -> "ALWAYS_ON_VPN";
                        case SHELL_SCRIPT_CONTROL -> "SHELL_SCRIPT_CONTROL";
                        default -> "Boot complete";
                    };

                    ServiceVPNHelper.start(reason, context);
                }, 2000);
            }
        }
    }

    private void broadcastDNSCryptState(boolean autoStartDNSCrypt) {
        if (autoStartDNSCrypt && modulesStatus.getDnsCryptState() == RUNNING) {
            modulesStatusBroadcaster.get().broadcastDNSCryptRunning();
        }
        if (autoStartDNSCrypt && modulesStatus.isDnsCryptReady()) {
            modulesStatusBroadcaster.get().broadcastDNSCryptReady();
        }
        if (!autoStartDNSCrypt && modulesStatus.getDnsCryptState() == STOPPED) {
            modulesStatusBroadcaster.get().broadcastDNSCryptStopped();
        }
    }

    private void broadcastTorState(boolean autoStartTor) {
        if (autoStartTor && modulesStatus.getTorState() == RUNNING) {
            modulesStatusBroadcaster.get().broadcastTorRunning();
        }
        if (autoStartTor && modulesStatus.isTorReady()) {
            modulesStatusBroadcaster.get().broadcastTorReady();
        }
        if (!autoStartTor && modulesStatus.getTorState() == STOPPED) {
            modulesStatusBroadcaster.get().broadcastTorStopped();
        }
    }

    private void broadcastItpdState(boolean autoStartItpd) {
        if (autoStartItpd && modulesStatus.getItpdState() == RUNNING) {
            modulesStatusBroadcaster.get().broadcastI2PDRunning();
        }
        if (autoStartItpd && modulesStatus.isItpdReady()) {
            modulesStatusBroadcaster.get().broadcastI2PDReady();
        }
        if (!autoStartItpd && modulesStatus.getItpdState() == STOPPED) {
            modulesStatusBroadcaster.get().broadcastI2PDStopped();
        }
    }

    private void broadcastControlDisabled() {
        modulesStatusBroadcaster.get().broadcastRemoteControlDisabled();
        logw("BootCompleteReceiver received SHELL_CONTROL, but the appropriate option is disabled!");
    }

    private void shortenTooLongITPDLog() {
        FileShortener.shortenTooTooLongFile(appDataDir + "/logs/i2pd.log");
    }

    private void startHOTSPOT(PreferenceRepository preferences) {

        preferences.setBoolPreference(PreferenceKeys.WIFI_ACCESS_POINT_IS_ON, true);

        if (!apManager.get().configApState()) {
            Intent intent_tether = new Intent(Intent.ACTION_MAIN, null);
            intent_tether.addCategory(Intent.CATEGORY_LAUNCHER);
            ComponentName cn = new ComponentName("com.android.settings", "com.android.settings.TetherSettings");
            intent_tether.setComponent(cn);
            intent_tether.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(intent_tether);
            } catch (Exception e) {
                loge("BootCompleteReceiver startHOTSPOT", e);
            }
        }
    }

    private void startStopRestartModules(
            boolean autoStartDNSCrypt,
            boolean autoStartTor,
            boolean autoStartITPD,
            boolean autoStartFirewall
    ) {

        ModulesStatus modulesStatus = ModulesStatus.getInstance();

        if (autoStartDNSCrypt) {
            runDNSCrypt();
            modulesStatus.setIptablesRulesUpdateRequested(true);
        } else if (ModulesAux.isDnsCryptSavedStateRunning()) {
            stopDNSCrypt();
        } else {
            modulesStatus.setDnsCryptState(STOPPED);
        }

        if (autoStartTor) {
            runTor();
            modulesStatus.setIptablesRulesUpdateRequested(true);
        } else if (ModulesAux.isTorSavedStateRunning()) {
            stopTor();
        } else {
            modulesStatus.setTorState(STOPPED);
        }

        if (autoStartITPD) {
            runITPD();
            modulesStatus.setIptablesRulesUpdateRequested(true);
        } else if (ModulesAux.isITPDSavedStateRunning()) {
            stopITPD();
        } else {
            modulesStatus.setItpdState(STOPPED);
        }

        if (autoStartFirewall) {
            modulesStatus.setFirewallState(STARTING, preferenceRepository.get());
            if (!autoStartDNSCrypt && !autoStartTor) {
                ModulesAux.makeModulesStateExtraLoop(context);
            }
        } else {
            modulesStatus.setFirewallState(STOPPED, preferenceRepository.get());
        }

        saveModulesStateRunning(autoStartDNSCrypt, autoStartTor, autoStartITPD);

    }

    private void saveModulesStateRunning(boolean saveDNSCryptRunning, boolean saveTorRunning, boolean saveITPDRunning) {
        ModulesAux.saveDNSCryptStateRunning(saveDNSCryptRunning);
        ModulesAux.saveTorStateRunning(saveTorRunning);
        ModulesAux.saveITPDStateRunning(saveITPDRunning);
    }

    private void resetModulesSavedState(PreferenceRepository preferences) {
        preferences.setStringPreference(SAVED_DNSCRYPT_STATE_PREF, ModuleState.UNDEFINED.toString());
        preferences.setStringPreference(SAVED_TOR_STATE_PREF, ModuleState.UNDEFINED.toString());
        preferences.setStringPreference(SAVED_ITPD_STATE_PREF, ModuleState.UNDEFINED.toString());
        ModulesAux.saveFirewallStateRunning(false);
    }

    private void runDNSCrypt() {
        ModulesRunner.runDNSCrypt(context);
    }

    private void runTor() {
        ModulesRunner.runTor(context);
    }

    private void runITPD() {
        ModulesRunner.runITPD(context);
    }

    private void stopDNSCrypt() {
        ModulesKiller.stopDNSCrypt(context);
    }

    private void stopTor() {
        ModulesKiller.stopTor(context);
    }

    private void stopITPD() {
        ModulesKiller.stopITPD(context);
    }

    private void stopServicesForeground(Context context, OperationMode mode, boolean fixTTL) {

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (mode == VPN_MODE || mode == ROOT_MODE && fixTTL) {
                Intent stopVPNServiceForeground = new Intent(context, VpnService.class);
                stopVPNServiceForeground.setAction(ACTION_STOP_SERVICE_FOREGROUND);
                stopVPNServiceForeground.putExtra("showNotification", true);
                if (App.getInstance().isAppForeground()) {
                    try {
                        context.startService(stopVPNServiceForeground);
                    } catch (Exception e) {
                        loge("BootCompleteReceiver stopServicesForeground", e);
                        context.startForegroundService(stopVPNServiceForeground);
                    }
                } else {
                    context.startForegroundService(stopVPNServiceForeground);
                }
            }

            ModulesActionSender.INSTANCE.sendIntent(context, ACTION_STOP_SERVICE_FOREGROUND);

            logi("BootCompleteReceiver stop running services foreground");
        }
    }
}
