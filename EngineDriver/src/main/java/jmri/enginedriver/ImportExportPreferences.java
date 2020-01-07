/* Copyright (C) 2017 M. Steve Todd mstevetodd@gmail.com

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

 * This helper class for importing and exporting shared preferences to SD card files.
 * Created by Peter Akers on 17-Dec-17.
*/

package jmri.enginedriver;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

public class ImportExportPreferences {

    boolean currentlyImporting = false;

    //private String exportedPreferencesFileName =  "exported_preferences.ed";

    private ArrayList<Integer> recent_loco_address_list;
    private ArrayList<Integer> recent_loco_address_size_list; // Look at address_type.java
    private ArrayList<String> recent_loco_name_list;
    private ArrayList<Integer> recent_loco_source_list;

    ArrayList<ArrayList<Integer>> consistEngineAddressList = new ArrayList<>();
    ArrayList<ArrayList<Integer>> consistAddressSizeList = new ArrayList<>();
    ArrayList<ArrayList<Integer>> consistDirectionList = new ArrayList<>();
    ArrayList<ArrayList<Integer>> consistSourceList = new ArrayList<>();
    ArrayList<ArrayList<String>> consistRosterNameList = new ArrayList<>();
    ArrayList<ArrayList<Integer>> consistLightList = new ArrayList<>();  // placeholder - not currently use
    ArrayList<String> consistNameList = new ArrayList<>();

    private static final int WHICH_SOURCE_UNKNOWN = 0;
//    private static final int WHICH_SOURCE_ADDRESS = 1;
//    private static final int WHICH_SOURCE_ROSTER = 2;

//    private static final int LIGHT_OFF = 0;
//    private static final int LIGHT_FOLLOW = 1;
      private static final int LIGHT_UNKNOWN = 2;

    private static final int FORCED_RESTART_REASON_NONE = 0;

    private static final String PREF_IMPORT_ALL_FULL = "Yes";
    private static final String PREF_IMPORT_ALL_PARTIAL = "No";
    private static final String PREF_IMPORT_ALL_RESET = "-";

    private boolean writeExportFile(Context context, SharedPreferences sharedPreferences, String exportedPreferencesFileName){
        Log.d("Engine_Driver", "writeExportFile: ImportExportPreferences: Writing export file");
        boolean res = false;
        ObjectOutputStream output = null;

        File path = Environment.getExternalStorageDirectory();
        File engine_driver_dir = new File(path, "engine_driver");
        engine_driver_dir.mkdir();            // create directory if it doesn't exist

        File dst = new File(path, "engine_driver/"+exportedPreferencesFileName);

        try {
            output = new ObjectOutputStream(new FileOutputStream(dst));
            output.writeObject(sharedPreferences.getAll());
            String m = context.getResources().getString(R.string.toastImportExportExportSucceeded,exportedPreferencesFileName);
            Toast.makeText(context, m, Toast.LENGTH_SHORT).show();
            Log.d("Engine_Driver", m);
            res = true;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (output != null) {
                    output.flush();
                    output.close();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        if (!res) {
            Log.e("Engine_Driver", "writeExportFile: ImportExportPreferences: Export Failed");
            Toast.makeText(context, "Export failed!", Toast.LENGTH_LONG).show();
        } else {
            Log.d("Engine_Driver", "writeExportFile: ImportExportPreferences: Export succeeded");

        }
        return res;
    }

    boolean saveSharedPreferencesToFile(Context context, SharedPreferences sharedPreferences, String exportedPreferencesFileName) {
        Log.d("Engine_Driver", "saveSharedPreferencesToFile: ImportExportPreferences: Saving preferences to file");
        boolean res = false;

        boolean prefImportExportLocoList = sharedPreferences.getBoolean("prefImportExportLocoList", context.getResources().getBoolean(R.bool.prefImportExportLocoListDefaultValue));
        if (prefImportExportLocoList) {
            recent_loco_address_list = new ArrayList<>();
            recent_loco_address_size_list = new ArrayList<>();
            recent_loco_name_list = new ArrayList<>();
            recent_loco_source_list = new ArrayList<>();
            getRecentLocosListFromFile();
            saveIntListDataToPreferences(recent_loco_address_list, "prefRecentLoco", sharedPreferences);
            saveIntListDataToPreferences(recent_loco_address_size_list, "prefRecentLocoSize", sharedPreferences);
            saveStringListDataToPreferences(recent_loco_name_list, "prefRecentLocoName", sharedPreferences);
            saveIntListDataToPreferences(recent_loco_source_list, "prefRecentLocoSource", sharedPreferences);

            getRecentConsistsListFromFile();
            saveStringListDataToPreferences(consistNameList, "prefRecentConsistName", sharedPreferences);
            for (int i = 0; i < consistNameList.size(); i++) {
                saveIntListDataToPreferences(consistEngineAddressList.get(i), "prefRecentConsistAddress_"+i, sharedPreferences);
                saveIntListDataToPreferences(consistAddressSizeList.get(i), "prefRecentConsistSize_"+i, sharedPreferences);
                saveIntListDataToPreferences(consistDirectionList.get(i), "prefRecentConsistDirection_"+i, sharedPreferences);
                saveIntListDataToPreferences(consistSourceList.get(i), "prefRecentConsistSource_"+i, sharedPreferences);
                saveStringListDataToPreferences(consistRosterNameList.get(i), "prefRecentConsistRosterName_"+i, sharedPreferences);
                saveIntListDataToPreferences(consistLightList.get(i), "prefRecentConsistLight_"+i, sharedPreferences);
            }
        }

        if (!exportedPreferencesFileName.equals(".ed")) {
            res = writeExportFile(context, sharedPreferences, exportedPreferencesFileName);
        } else {
            Toast.makeText(context, context.getResources().getString(R.string.toastImportExportExportFailed), Toast.LENGTH_LONG).show();
        }

        Log.d("Engine_Driver", "saveSharedPreferencesToFile: ImportExportPreferences: Saving preferences to file - Finished");
        return res;
    }

    @SuppressLint("ApplySharedPref")
    @SuppressWarnings({ "unchecked" })
    boolean loadSharedPreferencesFromFile(Context context, SharedPreferences sharedPreferences, String exportedPreferencesFileName, String deviceId) {
        Log.d("Engine_Driver", "loadSharedPreferencesFromFile: ImportExportPreferences: Loading saved preferences from file");
        currentlyImporting = true;
        boolean res = false;
        boolean srcExists = false;
        SharedPreferences.Editor prefEdit = sharedPreferences.edit();

        if (!exportedPreferencesFileName.equals(".ed")) {

            // save a few values so that we can reset them. i.e. efffectively don't import them
            String currentThrottleNameValue = sharedPreferences.getString("throttle_name_preference", context.getResources().getString(R.string.prefThrottleNameDefaultValue)).trim();
            String prefAutoImportExport = sharedPreferences.getString("prefAutoImportExport", context.getResources().getString(R.string.prefAutoImportExportDefaultValue));
            boolean prefForcedRestart = sharedPreferences.getBoolean("prefForcedRestart", false);
            int prefForcedRestartReason = sharedPreferences.getInt("prefForcedRestartReason", FORCED_RESTART_REASON_NONE);
            boolean prefImportExportLocoList = sharedPreferences.getBoolean("prefImportExportLocoList", context.getResources().getBoolean(R.bool.prefImportExportLocoListDefaultValue));
            String prefPreferencesImportFileName = sharedPreferences.getString("prefPreferencesImportFileName", "");

            String prefPreferencesImportAll = sharedPreferences.getString("prefPreferencesImportAll", PREF_IMPORT_ALL_FULL);
            String prefTheme = "";
            String prefThrottleScreenType = "";
            boolean prefDisplaySpeedButtons = false;
            boolean prefHideSlider = false;
            if (prefPreferencesImportAll.equals(PREF_IMPORT_ALL_PARTIAL)) { // save some additional prefereneces for restoration
                prefTheme = sharedPreferences.getString("prefTheme", "");
                prefThrottleScreenType = sharedPreferences.getString("prefThrottleScreenType", "");
                prefDisplaySpeedButtons = sharedPreferences.getBoolean("prefDisplaySpeedButtons", false);
                prefHideSlider = sharedPreferences.getBoolean("prefHideSlider", false);
            }


            File path = Environment.getExternalStorageDirectory();
            File engine_driver_dir = new File(path, "engine_driver");

            File src = new File(path, "engine_driver/" + exportedPreferencesFileName);

            if (src.exists()) {
                srcExists = true;

                ObjectInputStream input = null;
                try {
                    String restoredDeviceId = "";
                    input = new ObjectInputStream(new FileInputStream(src));
                    prefEdit.clear();


                    int i = 0;
                    Map<String, ?> entries = (Map<String, ?>) input.readObject();
                    Log.d("Engine_Driver", "loadSharedPreferencesFromFile: Key Count:" + entries.size());
                    for (Map.Entry<String, ?> entry : entries.entrySet()) {
                        Object v = entry.getValue();
                        String key = entry.getKey();

//                        Log.d("Engine_Driver", "ImportExportPreferences: loadSharedPreferencesFromFile: Key Start: " + key);

                        if (v instanceof Boolean) {
//                            Log.d("Engine_Driver", "ImportExportPreferences: loadSharedPreferencesFromFile: Key End: " + key + " - boolean - " + v);
                            prefEdit.putBoolean(key, (Boolean) v);
                        } else if (v instanceof Float) {
//                            Log.d("Engine_Driver", "ImportExportPreferences: loadSharedPreferencesFromFile: Key End: " + key + " - Float - " + v);
                            prefEdit.putFloat(key, (Float) v);
                        } else if (v instanceof Integer) {
//                            Log.d("Engine_Driver", "ImportExportPreferences: loadSharedPreferencesFromFile: Key End: " + key + " - Integer - " + v);
                            prefEdit.putInt(key, (Integer) v);
                        } else if (v instanceof Long) {
//                            Log.d("Engine_Driver", "ImportExportPreferences: loadSharedPreferencesFromFile: Key End: " + key + " - Long - " + v);
                            prefEdit.putLong(key, (Long) v);
                        } else if (v instanceof String) {
//                            Log.d("Engine_Driver", "ImportExportPreferences: loadSharedPreferencesFromFile: Key End: " + key + " - String - " + v);
                            prefEdit.putString(key, ((String) v));
                            if (key.equals("prefAndroidId")) { restoredDeviceId = (String) v;}
                        }

                        Log.d("Engine_Driver", "ImportExportPreferences: loadSharedPreferencesFromFile: Key " + i +" End: " + key + " - " + v);
                        i++;
                    }
                    res = true;


                    // restore the remembered throttle name to avoid a duplicate throttle name if this is a different to device to where it was originally saved
                    if ((!restoredDeviceId.equals(deviceId)) || (restoredDeviceId.equals(""))) {
                        prefEdit.putString("throttle_name_preference", currentThrottleNameValue);
                    }
                    prefEdit.putString("prefImportExport", "None");  //reset the preference
                    prefEdit.putString("prefHostImportExport", "None");  //reset the preference
                    prefEdit.putString("prefAutoImportExport", prefAutoImportExport);  //reset the preference
                    prefEdit.putBoolean("prefImportExportLocoList", prefImportExportLocoList);  //reset the preference
                    prefEdit.putString("prefRunIntro", threaded_application.INTRO_VERSION);  //don't re-run the intro
                    prefEdit.putBoolean("prefForcedRestart", true);
                    prefEdit.putInt("prefForcedRestartReason", prefForcedRestartReason);
                    prefEdit.putString("prefPreferencesImportFileName", prefPreferencesImportFileName);  //reset the preference

                    if (prefPreferencesImportAll.equals(PREF_IMPORT_ALL_PARTIAL)) { // save some additional preferences for restoration
                        prefEdit.putString("prefTheme", prefTheme);
                        prefEdit.putString("prefThrottleScreenType", prefThrottleScreenType);
                        prefEdit.putBoolean("prefDisplaySpeedButtons", prefDisplaySpeedButtons);
                        prefEdit.putBoolean("prefHideSlider", prefHideSlider);
                    }
                    prefEdit.putString("prefPreferencesImportAll", PREF_IMPORT_ALL_RESET); // reset the preference

                    String m = context.getResources().getString(R.string.toastImportExportImportSucceeded, exportedPreferencesFileName);

                    Log.d("Engine_Driver", "ImportExportPreferences: " + m);
                    Toast.makeText(context, m, Toast.LENGTH_LONG).show();

                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                    Log.e("Engine_Driver", "ImportExportPreferences: loadSharedPreferencesFromFile: " + e);
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.e("Engine_Driver", "ImportExportPreferences: loadSharedPreferencesFromFile: " + e);
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                    Log.e("Engine_Driver", "ImportExportPreferences: loadSharedPreferencesFromFile: " + e);
                } finally {
                    try {
                        if (input != null) {
                            input.close();
                        }
                    } catch (IOException ex) {
                        ex.printStackTrace();
                        Log.e("Engine_Driver", "ImportExportPreferences: loadSharedPreferencesFromFile: " + ex);
                    }
                }
                currentlyImporting = false;

                if (prefImportExportLocoList) {
                    // now take the recent locos list that was stored in the preferences and push them in the file
                    recent_loco_address_list = new ArrayList<>();
                    recent_loco_address_size_list = new ArrayList<>();
                    recent_loco_name_list = new ArrayList<>();
                    recent_loco_source_list = new ArrayList<>();
                    getIntListDataFromPreferences(recent_loco_address_list, "prefRecentLoco", sharedPreferences);
                    getIntListDataFromPreferences(recent_loco_address_size_list, "prefRecentLocoSize", sharedPreferences);
                    getStringListDataFromPreferences(recent_loco_name_list, "prefRecentLocoName", sharedPreferences);
                    getIntListDataFromPreferences(recent_loco_source_list, "prefRecentLocoSource", sharedPreferences);
                    writeRecentLocosListToFile(sharedPreferences);

                    getStringListDataFromPreferences(consistNameList, "prefRecentConsistName", sharedPreferences);
                    for (int i = 0; i < consistNameList.size(); i++) {
                        ArrayList<Integer> tempConsistEngineAddressList_inner = new ArrayList<>();
                        ArrayList<Integer> tempConsistAddressSizeList_inner = new ArrayList<>();
                        ArrayList<Integer> tempConsistDirectionList_inner = new ArrayList<>();
                        ArrayList<Integer> tempConsistSourceList_inner = new ArrayList<>();
                        ArrayList<String> tempConsistRosterNameList_inner = new ArrayList<>();
                        ArrayList<Integer> tempConsistLightList_inner = new ArrayList<>();

                        getIntListDataFromPreferences(tempConsistEngineAddressList_inner, "prefRecentConsistAddress_"+i, sharedPreferences);
                        getIntListDataFromPreferences(tempConsistAddressSizeList_inner, "prefRecentConsistSize_"+i, sharedPreferences);
                        getIntListDataFromPreferences(tempConsistDirectionList_inner, "prefRecentConsistDirection_"+i, sharedPreferences);
                        getIntListDataFromPreferences(tempConsistSourceList_inner, "prefRecentConsistSource_"+i, sharedPreferences);
                        getStringListDataFromPreferences(tempConsistRosterNameList_inner, "prefRecentConsistRosterName_"+i, sharedPreferences);
                        getIntListDataFromPreferences(tempConsistLightList_inner, "prefRecentConsistLight_"+i, sharedPreferences);

                        consistEngineAddressList.add(tempConsistEngineAddressList_inner);
                        consistAddressSizeList.add(tempConsistAddressSizeList_inner);
                        consistDirectionList.add(tempConsistDirectionList_inner);
                        consistSourceList.add(tempConsistSourceList_inner);
                        consistRosterNameList.add(tempConsistRosterNameList_inner);
                        consistLightList.add(tempConsistLightList_inner);
                    }
                    writeRecentConsistsListToFile(sharedPreferences, -1);
                }
            }
            if (!res) {
                if (srcExists) {
                    Toast.makeText(context, context.getResources().getString(R.string.toastImportExportImportFailed, exportedPreferencesFileName), Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(context, context.getResources().getString(R.string.toastImportExportServerImportFailed, exportedPreferencesFileName), Toast.LENGTH_LONG).show();
                }
            }
        } else {
            Toast.makeText(context, context.getResources().getString(R.string.toastImportExportCannotImport), Toast.LENGTH_LONG).show();
        }

        prefEdit.commit();
        Log.d("Engine_Driver", "loadSharedPreferencesFromFile: ImportExportPreferences: Loading saved preferences from file - Finished");
        return res;
    }

    // simliar, but different, code exists in select_loco.java. if you modify one, make sure you modify the other
    private void getRecentLocosListFromFile() {
        Log.d("Engine_Driver", "getRecentLocosListFromFile: ImportExportPreferences: Loading recent locos list from file");
        try {
            // Populate the List with the recent engines saved in a file. This
            // will be stored in /sdcard/engine_driver/recent_engine_list.txt
            File sdcard_path = Environment.getExternalStorageDirectory();
            File engine_list_file = new File(sdcard_path + "/engine_driver/recent_engine_list.txt");
            if (engine_list_file.exists()) {
                BufferedReader list_reader = new BufferedReader(
                        new FileReader(engine_list_file));
                while (list_reader.ready()) {
                    String line = list_reader.readLine();
                    int splitPos = line.indexOf(':');
                    if (splitPos > 0) {
                        Integer addr, size, source = 0;
                        String locoName = "";
                        try {
                            addr = Integer.decode(line.substring(0, splitPos));
                            size = Integer.decode(line.substring(splitPos + 1, splitPos + 2));
                            if (line.length()>splitPos+2) { // has the name extras
                                if (line.substring(splitPos + 2,splitPos + 3).equals("~")) { // old format
                                    locoName = line.substring(splitPos + 3);
                                }else {
                                    if (line.substring(splitPos + 3,splitPos + 4).equals("~")) { // new format. Includes the source
                                        source = Integer.decode(line.substring(splitPos + 2,splitPos + 3));
                                        locoName = line.substring(splitPos + 4);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            addr = -1;
                            size = -1;
                            locoName = "";
                            source = -1;
                        }
                        if ((addr >= 0) && (size >= 0)) {
                            recent_loco_address_list.add(addr);
                            recent_loco_address_size_list.add(size);
                            String addressLengthString = ((size == 0) ? "S" : "L");  //show L or S based on length from file
                            String engineAddressString = String.format("%s(%s)", addr.toString(), addressLengthString);
                            if ((locoName.length()==0  || locoName.equals(engineAddressString))) { // if nothing is stored, or what is stored is the same as the address, look for it in the roster
                                locoName = engineAddressString;
                            }
                            recent_loco_name_list.add(locoName);
                            recent_loco_source_list.add(source);

                        }
                    }
                }
                list_reader.close();
            }
            Log.d("Engine_Driver", "getRecentLocosListFromFile: ImportExportPreferences: Read recent locos list from file complete successfully");

        } catch (IOException except) {
            Log.e("Engine_Driver", "getRecentLocosListFromFile: ImportExportPreferences: select_loco - Error reading recent loco file. "
                    + except.getMessage());
        }
    }

    // simliar, but different, code exists in select_loco.java, ImportExportPreferences.java and ConsistLightsEdit.java. if you modify one, make sure you modify the other
    private void writeRecentLocosListToFile(SharedPreferences sharedPreferences) {
        Log.d("Engine_Driver", "writeRecentLocosListToFile: ImportExportPreferences: Writing recent locos list to file");

        // write it out from the saved preferences to the file
        File sdcard_path = Environment.getExternalStorageDirectory();
        File engine_list_file = new File(sdcard_path,
                "engine_driver/recent_engine_list.txt");

        PrintWriter list_output;
        String smrl = sharedPreferences.getString("maximum_recent_locos_preference", "10"); //retrieve pref for max recent locos to show
        try {
            int mrl = Integer.parseInt(smrl);
            list_output = new PrintWriter(engine_list_file);
            if (mrl > 0) {
                for (int i = 0; i < recent_loco_address_list.size(); i++) {
//                    list_output.format("%d:%d\n", recent_loco_address_list.get(i), recent_loco_address_size_list.get(i));
                    list_output.format("%d:%d%d~%s\n",
                            recent_loco_address_list.get(i),
                            recent_loco_address_size_list.get(i),
                            recent_loco_source_list.get(i),
                            recent_loco_name_list.get(i));
                }
            }
            list_output.flush();
            list_output.close();
            Log.d("Engine_Driver", "writeRecentLocosListToFile: ImportExportPreferences: Write recent locos list to file complete successfully");
        } catch (IOException except) {
            Log.e("Engine_Driver",
                    "writeRecentLocosListToFile: ImportExportPreferences: Error creating a PrintWriter, IOException: "
                            + except.getMessage());
        }
    }

    // simliar, but different, code exists in select_loco.java, ImportExportPreferences.java. if you modify one, make sure you modify the other
    void getRecentConsistsListFromFile() {
        Log.d("Engine_Driver", "getRecentConsistsListFromFile: ImportExportPreferences: Loading recent consists list from file");

        consistEngineAddressList = new ArrayList<>();
        consistAddressSizeList = new ArrayList<>();
        consistDirectionList = new ArrayList<>();
        consistLightList = new ArrayList<>();
        consistSourceList = new ArrayList<>();
        consistRosterNameList = new ArrayList<>();
        consistNameList = new ArrayList<>();

        ArrayList<Integer> tempConsistEngineAddressList_inner;
        ArrayList<Integer> tempConsistAddressSizeList_inner;
        ArrayList<Integer> tempConsistDirectionList_inner;
        ArrayList<Integer> tempConsistSourceList_inner;
        ArrayList<String> tempConsistRosterNameList_inner;
        ArrayList<Integer> tempConsistLightList_inner;

        //if no SD Card present then there is no recent consists list
        if (android.os.Environment.getExternalStorageState().equals(android.os.Environment.MEDIA_MOUNTED)) {
            try {
                // Populate the List with the recent engines saved in a file. This
                // will be stored in /sdcard/engine_driver/recent_consist_list.txt
                File sdcard_path = Environment.getExternalStorageDirectory();
                File consist_list_file = new File(sdcard_path + "/engine_driver/recent_consist_list.txt");
                if (consist_list_file.exists()) {
                    BufferedReader list_reader = new BufferedReader(
                            new FileReader(consist_list_file));
                    while (list_reader.ready()) {
                        StringBuilder oneConsist = new StringBuilder();
                        String line = list_reader.readLine();
                        tempConsistEngineAddressList_inner = new ArrayList<>();
                        tempConsistAddressSizeList_inner = new ArrayList<>();
                        tempConsistDirectionList_inner = new ArrayList<>();
                        tempConsistSourceList_inner = new ArrayList<>();
                        tempConsistRosterNameList_inner = new ArrayList<>();
                        tempConsistLightList_inner = new ArrayList<>();

                        String consistName = "";
                        String splitOn = "<~>";
                        if (line.indexOf("<~>") == -1) { // must be the old format
                            splitOn = "~";
                        }
                        String[] splitLine = line.split(splitOn, -1);

                        if (splitLine.length > 1) { // see if there is a name saved as well
                            consistName = splitLine[1];
                            line = splitLine[0];
                            if (splitLine.length > 2) {  // see if there is roster names saved as well
                                String[] rosterNames = splitLine[2].split("<,>", -1);
                                tempConsistRosterNameList_inner.addAll(Arrays.asList(rosterNames));
                            }
                        }

                        int splitLoco = line.indexOf(',');
                        if (splitLoco != -1) {
                            oneConsist.append(addOneConsistAddress(line, 0, splitLoco,
                                    tempConsistEngineAddressList_inner,
                                    tempConsistAddressSizeList_inner,
                                    tempConsistDirectionList_inner,
                                    tempConsistSourceList_inner,
                                    tempConsistLightList_inner));

                            boolean foundOne = true;
                            while (foundOne) {
                                Integer prevSplitLoco = splitLoco + 1;
                                splitLoco = line.indexOf(',', prevSplitLoco);
                                if (splitLoco != -1) {
                                    oneConsist.append(addOneConsistAddress(line, prevSplitLoco, splitLoco,
                                            tempConsistEngineAddressList_inner,
                                            tempConsistAddressSizeList_inner,
                                            tempConsistDirectionList_inner,
                                            tempConsistSourceList_inner,
                                            tempConsistLightList_inner));
                                } else {
                                    oneConsist.append(addOneConsistAddress(line, prevSplitLoco, line.length(),
                                            tempConsistEngineAddressList_inner,
                                            tempConsistAddressSizeList_inner,
                                            tempConsistDirectionList_inner,
                                            tempConsistSourceList_inner,
                                            tempConsistLightList_inner));
                                    foundOne = false;
                                }
                            }
                            if (splitLine.length < 3) {  // old format - need to add some dummy roster names
                                for (int j = 0; j < tempConsistEngineAddressList_inner.size(); j++) {
                                    tempConsistRosterNameList_inner.add("");
                                }
                            }
                            consistEngineAddressList.add(tempConsistEngineAddressList_inner);
                            consistAddressSizeList.add(tempConsistAddressSizeList_inner);
                            consistDirectionList.add(tempConsistDirectionList_inner);
                            consistSourceList.add(tempConsistSourceList_inner);
                            consistRosterNameList.add(tempConsistRosterNameList_inner);
                            consistLightList.add(tempConsistLightList_inner);
                            if (consistName.length() == 0) {
                                consistName = oneConsist.toString();
                            }
                            consistNameList.add(consistName);
                        }
                    }
                    list_reader.close();
                    Log.d("Engine_Driver", "getRecentConsistsListFromFile: ImportExportPreferences: Read recent consists list from file completed successfully");
                }

            } catch (IOException except) {
                Log.e("Engine_Driver", "getRecentConsistsListFromFile: ImportExportPreferences: Error reading recent consist file. "
                        + except.getMessage());
            }
        }
    }

    // simliar, but different, code exists in select_loco.java, ImportExportPreferences.java. if you modify one, make sure you modify the other
    private String addOneConsistAddress(String line, Integer start, Integer end,
                                        ArrayList<Integer> tempConsistEngineAddressList_inner,
                                        ArrayList<Integer> tempConsistAddressSizeList_inner,
                                        ArrayList<Integer> tempConsistDirectionList_inner,
                                        ArrayList<Integer> tempConsistSourceList_inner,
                                        ArrayList<Integer> tempConsistLightList_inner) {
        String rslt = "";
        String splitLine = line.substring(start, end);
        int splitPos = splitLine.indexOf(':');
        if (splitPos!=-1) {
            Integer addr = Integer.decode(splitLine.substring(0, splitPos));
            int size = Integer.decode(splitLine.substring(splitPos + 1, splitPos + 2));
            int dir = Integer.decode(splitLine.substring(splitPos + 2, splitPos + 3));
            int source = WHICH_SOURCE_UNKNOWN; //default to unknown
            int light = LIGHT_UNKNOWN; //default to unknown
            if (splitLine.length()>splitPos + 3) {  // if short, then this is the first format that did not include the source or light value
                source = Integer.decode(splitLine.substring(splitPos + 3, splitPos + 4));
                light = Integer.decode(splitLine.substring(splitPos + 4, splitPos + 5));
            }
            tempConsistEngineAddressList_inner.add(addr);
            tempConsistAddressSizeList_inner.add(size);
            tempConsistDirectionList_inner.add(dir);
            tempConsistSourceList_inner.add(source);
            tempConsistLightList_inner.add(light);

            rslt = addr.toString();
        }
        return rslt;
    }

    // simliar, but different, code exists in select_loco.java. if you modify one, make sure you modify the other
    void writeRecentConsistsListToFile(SharedPreferences sharedPreferences, int whichEntryIsBeingUpdated) {
        Log.d("Engine_Driver", "writeRecentConsistsListToFile: ImportExportPreferences: Writing recent consists list to file");

        // write it out from the saved preferences to the file
        File sdcard_path = Environment.getExternalStorageDirectory();
        File consist_list_file = new File(sdcard_path,
                "engine_driver/recent_consist_list.txt");

        PrintWriter list_output;
//        String smrl = sharedPreferences.getString("maximum_recent_locos_preference", "10"); //retrieve pref for max recent locos to show
        int numberOfRecentLocosToWrite = preferences.getIntPrefValue(sharedPreferences, "maximum_recent_locos_preference", "10");
        try {
//            int numberOfRecentLocosToWrite = Integer.parseInt(smrl);
            list_output = new PrintWriter(consist_list_file);
            if (numberOfRecentLocosToWrite > 0) {
                for (int i = 0; i < consistNameList.size() && numberOfRecentLocosToWrite > 0; i++) {

                    if (i!=whichEntryIsBeingUpdated) { // if this is the one being updated, don't write it

                        for (int j = 0; j < consistAddressSizeList.get(i).size(); j++) {
                            if (j > 0) {
                                list_output.format(",");
                            }
                            list_output.format("%d:%d%d%d%d",
                                    consistEngineAddressList.get(i).get(j),
                                    consistAddressSizeList.get(i).get(j),
                                    consistDirectionList.get(i).get(j),
                                    consistSourceList.get(i).get(j),
                                    consistLightList.get(i).get(j));
                        }
                        list_output.format("<~>%s<~>", consistNameList.get(i));
                        for (int j = 0; j < consistRosterNameList.get(i).size(); j++) {
                            if (j > 0) {
                                list_output.format("<,>");
                            }
                            list_output.format("%s",
                                    consistRosterNameList.get(i).get(j));
                        }

                        list_output.format("\n");
                        numberOfRecentLocosToWrite--;
                    }
                }
            }
            list_output.flush();
            list_output.close();
            Log.d("Engine_Driver", "writeRecentConsistsListToFile: ImportExportPreferences: Write recent consists list to file completed successfully");
        } catch (IOException except) {
            Log.e("Engine_Driver",
                    "writeRecentConsistsListToFile: ImportExportPreferences: Error creating a PrintWriter, IOException: "
                            + except.getMessage());
        }
    }


    @SuppressLint("ApplySharedPref")
    private boolean saveIntListDataToPreferences(ArrayList<Integer> list, String listName, SharedPreferences sharedPreferences) {
        sharedPreferences.edit().putInt(listName +"_size", list.size()).commit();
        int prefInt;
        for(int i=0 ; i<list.size() ; i++){
            prefInt=list.get(i);
            sharedPreferences.edit().putInt(listName + "_" + i, prefInt).commit();
        }
        return sharedPreferences.edit().commit();
    }

    private int getIntListDataFromPreferences(ArrayList<Integer> list, String listName, SharedPreferences sharedPreferences) {
        int size = sharedPreferences.getInt(listName + "_size", 0);
        int prefInt;
        for(int i=0 ; i<size ; i++){
            prefInt = sharedPreferences.getInt(listName + "_" + i, 0);
            list.add(prefInt);
        }
        return size;
    }

    @SuppressLint("ApplySharedPref")
    private boolean saveStringListDataToPreferences(ArrayList<String> list, String listName, SharedPreferences sharedPreferences) {
        sharedPreferences.edit().putInt(listName +"_size", list.size()).commit();
        String prefString;
        for(int i=0 ; i<list.size() ; i++){
            prefString = list.get(i);
            sharedPreferences.edit().putString(listName + "_" + i, prefString).commit();
        }
        return sharedPreferences.edit().commit();
    }

    private int getStringListDataFromPreferences(ArrayList<String> list, String listName, SharedPreferences sharedPreferences) {
        int size = sharedPreferences.getInt(listName + "_size", 0);
        String prefString;
        for(int i=0 ; i<size ; i++){
            prefString = sharedPreferences.getString(listName + "_" + i, "");
            list.add(prefString);
        }
        return size;
    }

}
