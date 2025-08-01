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

package pan.alexander.tordnscrypt.help;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;
import androidx.fragment.app.DialogFragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.os.Looper;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;

import com.github.angads25.filepicker.model.DialogConfigs;
import com.github.angads25.filepicker.model.DialogProperties;
import com.github.angads25.filepicker.view.FilePickerDialog;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import dagger.Lazy;
import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.LangAppCompatActivity;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.dialogs.progressDialogs.PleaseWaitProgressDialog;
import pan.alexander.tordnscrypt.dialogs.NotificationDialogFragment;
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.enums.FileOperationsVariants;
import pan.alexander.tordnscrypt.utils.executors.CoroutineExecutor;
import pan.alexander.tordnscrypt.utils.filemanager.ExternalStoragePermissions;
import pan.alexander.tordnscrypt.utils.filemanager.FileManager;
import pan.alexander.tordnscrypt.utils.filemanager.OnBinaryFileOperationsCompleteListener;
import pan.alexander.tordnscrypt.utils.integrity.Verifier;
import pan.alexander.tordnscrypt.utils.root.RootCommands;
import pan.alexander.tordnscrypt.utils.root.RootExecService;
import pan.alexander.tordnscrypt.modules.ModulesStatus;

import static pan.alexander.tordnscrypt.utils.enums.FileOperationsVariants.deleteFile;
import static pan.alexander.tordnscrypt.utils.enums.FileOperationsVariants.moveBinaryFile;
import static pan.alexander.tordnscrypt.utils.logger.Logger.loge;
import static pan.alexander.tordnscrypt.utils.logger.Logger.logw;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.SAVE_ROOT_LOGS;
import static pan.alexander.tordnscrypt.utils.root.RootCommandsMark.HELP_ACTIVITY_MARK;

import javax.inject.Inject;

public class HelpActivity extends LangAppCompatActivity implements View.OnClickListener,
        CompoundButton.OnCheckedChangeListener, OnBinaryFileOperationsCompleteListener {

    @Inject
    public Lazy<PathVars> pathVarsLazy;
    @Inject
    public Lazy<PreferenceRepository> preferenceRepository;
    @Inject
    public CoroutineExecutor executor;
    @Inject
    public Lazy<Verifier> verifier;

    private TextView tvLogsPath;
    private EditText etLogsPath;
    private HelpActivityReceiver br;
    private String appDataDir;
    private String cacheDir;
    private String busyboxPath;
    private String pathToSaveLogs;
    private String iptables;
    private String appUID;
    private static DialogFragment dialogFragment;
    private ModulesStatus modulesStatus;
    private String info;
    private boolean logsDirAccessible;
    private ActivityManager activityManager;

    @SuppressLint("NewApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        App.getInstance().getDaggerComponent().inject(this);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_help);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        tvLogsPath = findViewById(R.id.tvLogsPath);

        etLogsPath = findViewById(R.id.etLogsPath);
        etLogsPath.setOnClickListener(this);

        hideSelectionEditTextIfRequired();

        Button btnSaveLogs = findViewById(R.id.btnSaveLogs);
        btnSaveLogs.setOnClickListener(this);
        btnSaveLogs.requestFocus();

        View dividerSaveLogs = findViewById(R.id.dividerSaveLogs);
        SwitchCompat swRootCommandsLog = findViewById(R.id.swRootCommandsLog);

        if (ModulesStatus.getInstance().isRootAvailable()) {
            swRootCommandsLog.setChecked(preferenceRepository.get().getBoolPreference(SAVE_ROOT_LOGS));
            swRootCommandsLog.setOnCheckedChangeListener(this);
        } else {
            swRootCommandsLog.setVisibility(View.GONE);
            dividerSaveLogs.setVisibility(View.GONE);
        }

        Handler mHandler = null;
        Looper looper = Looper.getMainLooper();
        if (looper != null) {
            mHandler = new Handler(looper);
        }

        activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);

        PathVars pathVars = pathVarsLazy.get();
        appDataDir = pathVars.getAppDataDir();
        busyboxPath = pathVars.getBusyboxPath();
        pathToSaveLogs = pathVars.getDefaultBackupPath();
        iptables = pathVars.getIptablesPath();
        appUID = pathVars.getAppUidStr();

        cacheDir = pathVars.getCacheDirPath(this);

        br = new HelpActivityReceiver(mHandler, appDataDir, cacheDir, pathToSaveLogs);

        modulesStatus = ModulesStatus.getInstance();

        if (modulesStatus.isRootAvailable()) {
            IntentFilter intentFilter = new IntentFilter(RootExecService.COMMAND_RESULT);
            LocalBroadcastManager.getInstance(this).registerReceiver(br, intentFilter);
        } else {
            swRootCommandsLog.setVisibility(View.GONE);
        }

    }

    @Override
    protected void onResume() {
        super.onResume();

        setTitle(R.string.drawer_menu_help);

        etLogsPath.setText(pathToSaveLogs);

        executor.submit("HelpActivity onResume", () -> {
            new File(cacheDir + "/logs").mkdirs();
            return null;
        });

        FileManager.setOnFileOperationCompleteListener(this);
    }

    @Override
    public void onClick(View view) {

        int id = view.getId();
        if (id == R.id.btnSaveLogs) {
            if (logsDirAccessible && !isWriteExternalStoragePermissions()) {
                requestWriteExternalStoragePermissions();
                return;
            }

            try {

                ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
                activityManager.getMemoryInfo(memoryInfo);

                info = Utils.INSTANCE.collectInfo(
                        verifier.get().getAppSignature(),
                        pathVarsLazy.get().getAppVersion(),
                        pathVarsLazy.get().getAppProcVersion(),
                        Utils.getAppVersion(this, pathVarsLazy.get(), preferenceRepository.get()),
                        memoryInfo
                );
            } catch (Exception ignored) {
            }
            br.setInfo(info);

            dialogFragment = PleaseWaitProgressDialog.getInstance();
            dialogFragment.show(getSupportFragmentManager(), "PleaseWaitProgressDialog");
            br.setProgressDialog(dialogFragment);

            if (modulesStatus.isRootAvailable()) {
                collectLogsMethodOne(info);
            } else {
                executor.submit("HelpActivity onClick", () -> {
                    br.saveLogs(getApplicationContext(), null).run();
                    return null;
                });
            }
        } else if (id == R.id.etLogsPath) {
            chooseOutputFolder();
        }
    }

    private void chooseOutputFolder() {
        DialogProperties properties = new DialogProperties();
        properties.selection_mode = DialogConfigs.SINGLE_MODE;
        properties.selection_type = DialogConfigs.DIR_SELECT;
        properties.root = new File(Environment.getExternalStorageDirectory().getPath());
        properties.error_dir = new File(pathVarsLazy.get().getCacheDirPath(this));
        properties.offset = new File(Environment.getExternalStorageDirectory().getPath());
        properties.extensions = null;

        FilePickerDialog dial = new FilePickerDialog(this, properties);
        dial.setTitle(R.string.backupFolder);
        dial.setDialogSelectionListener(files -> {
            pathToSaveLogs = files[0];
            etLogsPath.setText(pathToSaveLogs);
            br.setPathToSaveLogs(pathToSaveLogs);
        });
        dial.show();
    }

    private void requestWriteExternalStoragePermissions() {
        ExternalStoragePermissions permissions = new ExternalStoragePermissions(this);
        permissions.requestReadWritePermissions();
    }

    private boolean isWriteExternalStoragePermissions() {
        ExternalStoragePermissions permissions = new ExternalStoragePermissions(this);
        return permissions.isWritePermissions();
    }

    private void collectLogsMethodOne (String info) {
        int pid = android.os.Process.myPid();

        List<String> logcatCommands = new ArrayList<>(Arrays.asList(
                "cd " + cacheDir,
                busyboxPath + "rm -rf logs_dir 2> /dev/null || true",
                busyboxPath + "mkdir -m 655 -p logs_dir 2> /dev/null || true",
                busyboxPath + "cp -R " + appDataDir + "/logs" + " logs_dir 2> /dev/null || true",
                "logcat -d | grep " + pid + " > logs_dir/logcat.log 2> /dev/null || true",
                "ifconfig > logs_dir/ifconfig.log 2> /dev/null || true",
                busyboxPath + "cp -R "+ appDataDir + "/shared_prefs"+" logs_dir 2> /dev/null || true",
                busyboxPath + "sleep 1 2> /dev/null || true",
                busyboxPath + "echo \"" + info + "\" > logs_dir/device_info.log 2> /dev/null || true",
                iptables + "-L -v > logs_dir/filter.log 2> /dev/null || true",
                iptables + "-t nat -L -v > logs_dir/nat.log 2> /dev/null || true",
                iptables + "-t mangle -L -v > logs_dir/mangle.log 2> /dev/null || true",
                iptables + "-t raw -L -v > logs_dir/raw.log 2> /dev/null || true",
                "restorecon -R logs_dir 2> /dev/null || true",
                busyboxPath + "chown -R " + appUID + "." + appUID + " logs_dir 2> /dev/null || true",
                busyboxPath + "chmod -R 755 logs_dir 2> /dev/null || true",
                busyboxPath + "echo 'Logs Saved' 2> /dev/null || true"
        ));

        RootCommands.execute(this, logcatCommands, HELP_ACTIVITY_MARK);
    }


    @Override
    public void onPause() {
        super.onPause();

        FileManager.deleteOnFileOperationCompleteListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (modulesStatus.isRootAvailable() && br != null) {
            try {
                LocalBroadcastManager.getInstance(this).unregisterReceiver(br);
            } catch (Exception e) {
                logw("HelpActivity uregister receiver fault", e);
            }

        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {// API 5+ solution
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean newValue) {
        if (compoundButton.getId() == R.id.swRootCommandsLog) {
            preferenceRepository.get().setBoolPreference(SAVE_ROOT_LOGS, newValue);

            if (!newValue) {
                FileManager.deleteFile(getApplicationContext(), appDataDir + "/logs", "RootExec.log", "RootExec.log");
                FileManager.deleteFile(getApplicationContext(), appDataDir + "/logs", "Snowflake.log", "Snowflake.log");
                FileManager.deleteFile(getApplicationContext(), appDataDir + "/logs", "Conjure.log", "Conjure.log");
                FileManager.deleteFile(getApplicationContext(), appDataDir + "/logs", "WebTunnel.log", "WebTunnel.log");
            }
        }
    }

    @Override
    public void OnFileOperationComplete(FileOperationsVariants currentFileOperation, boolean fileOperationResult, String path, String tag) {
        if (currentFileOperation == deleteFile && !fileOperationResult) {
            loge("Unable to delete file " + path);
        } else if (currentFileOperation == moveBinaryFile) {

            if (dialogFragment != null) {
                dialogFragment.dismiss();
                dialogFragment = null;
            }

            if (fileOperationResult) {

                DialogFragment commandResult =
                        NotificationDialogFragment.newInstance(getText(R.string.help_activity_logs_saved).toString()
                                + " " + pathToSaveLogs);
                commandResult.show(getSupportFragmentManager(), "NotificationDialogFragment");
            } else {
                File logs = new File(cacheDir + "/logs/InvizibleLogs.txt");
                if (logs.isFile()) {
                    Uri uri = FileProvider.getUriForFile(this, this.getPackageName() + ".fileprovider", logs);
                    Utils.INSTANCE.sendMail(this, info, uri);
                }
            }
        }
    }

    private void hideSelectionEditTextIfRequired() {
        executor.submit("HelpActivity hideSelectionEditTextIfRequired", () -> {

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                logsDirAccessible = pan.alexander.tordnscrypt.utils.Utils.INSTANCE.isLogsDirAccessible();
            }

            if (!isFinishing() && !logsDirAccessible && etLogsPath != null && tvLogsPath != null) {
                runOnUiThread(() ->{
                    if (!isFinishing() && etLogsPath != null && tvLogsPath != null) {
                        tvLogsPath.setVisibility(View.GONE);
                        etLogsPath.setVisibility(View.GONE);
                    }

                });
            }
            return null;
        });
    }
}
