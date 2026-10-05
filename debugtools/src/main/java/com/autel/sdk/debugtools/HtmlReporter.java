package com.autel.sdk.debugtools;


import com.autel.drone.sdk.log.SDKLog;
import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.Keep;

import com.autel.drone.sdk.vmodelx.SDKManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
@Keep
@SuppressLint("SimpleDateFormat")
public class HtmlReporter {
    private static String G_HTMLLog = ""; // HTML Log
    private static String G_TCList = ""; // executed test cases
    private static int G_DONE_CNT = 0;
    private static int G_PASS_CNT = 0;
    private static int G_FAIL_CNT = 0;
    private static int G_WARNING_CNT = 0;
    private static int G_OTHERS_CNT = 0; // per-state counts of operation steps
    private static final List<Date> G_ExecDateTime = new ArrayList<Date>();
    private static final long systemTime = System.currentTimeMillis();

	private static final String nowDateTime = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date(systemTime));// used for report and screenshot paths

    final public static String DONE = "DONE";
    final public static String PASS = "PASS";
    final public static String FAIL = "FAIL";
    final public static String WARNING = "WARNING";

    static String tcName = "test result";



    /**
     * Record script runtime logs in HTML format
     *
     * @param sTCase   $sTCase test business
     * @param sDetails $sDetails operation description
     * @param sStatus  $sStatus operation status: done=success pass=verified fail=error warning=warning
     * @param sImg     $img screenshot file name
     * @return boolean
     */
    public static boolean writeHTMLLog(String sTCase, String sDetails, String sStatus, String sImg) {
        String cFont = "";
        String imgLink = "";// full image path

        sTCase = htmlspecialchars(sTCase);
        sDetails = htmlspecialchars(sDetails);


        Date date1 = new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 8);
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String datetimeString = df.format(date1);
        G_ExecDateTime.add(date1);

        if (sImg != null && sImg != "") {
            //imgLink = "E:/test/" + nowDateTime + "/" + sImg+".png" ;
            imgLink = sImg + ".png";
        }

        sStatus = sStatus.toUpperCase();

        switch (sStatus) {
            case "DONE":
                //G_DONE_CNT++;
                cFont = "#000000";
                break;
            case "PASS":
                G_PASS_CNT++;
                G_DONE_CNT++;
                cFont = "#008000";
                break;
            case "FAIL":
                G_FAIL_CNT++;
                G_DONE_CNT++;
                cFont = "#ff0000";
                break;
            case "WARNING":
                G_WARNING_CNT++;
                G_DONE_CNT++;
                cFont = "#0000ff";
                break;
            default:
                G_OTHERS_CNT++;
                cFont = "#969696";
                break;
        }

        G_HTMLLog = G_HTMLLog + "<tr>";
        G_HTMLLog = G_HTMLLog + "<td>" + datetimeString + "</td>";
        G_HTMLLog = G_HTMLLog + "<td>" + sTCase + "</td>";
        G_HTMLLog = G_HTMLLog + "<td><b><font color=" + cFont + ">" + sStatus + "</font></b></td>";
        G_HTMLLog = G_HTMLLog + "<td>" + sDetails + "</td>";
        if (!TextUtils.isEmpty(sImg)) {
            G_HTMLLog = G_HTMLLog + "<td><a href=\"" + imgLink + "\" target =\"_blank\"><img src=" + imgLink + " width=50 hight=50></a></td>";
        } else {
            G_HTMLLog = G_HTMLLog + "<td></td>";
        }
        G_HTMLLog = G_HTMLLog + "</tr>";

        return true;
    }

    /**
     * HTML tag escaping
     *
     * @param html html
     * @return String
     */
    private static String htmlspecialchars(String html) {
        if (html == null) {
            return "";
        }
        Map<String, String> special = new HashMap<String, String>();
        special.put("&", "&amp;");
        special.put("\"", "&quot;");
        special.put("'", "&#039;");
        special.put("<", "&lt;");
        special.put(">", "&gt;");
        Iterator<Entry<String, String>> iter = special.entrySet().iterator();
        while (iter.hasNext()) {
            Entry<String, String> entry = iter.next();
            String key = entry.getKey();
            String val = entry.getValue();
            if (html.contains(key)) {
                html.replace(key, val);
            }
        }
        return html;
    }

    /**
     * Generate the report
     */
	@SuppressLint("SimpleDateFormat")
	public static String seleniumReport(Context context) {
        int TOTAL_CNT = 0;
        String sLog = "";
        String timeDiff = "";
        long quot = 0;
        String firstDateTime = "";
        String lastDateTime = "";

        // java.util.Date date1=new java.util.Date();

        if (!G_ExecDateTime.isEmpty()) {
            Collections.sort(G_ExecDateTime); // sort by execution time
           DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            firstDateTime = df.format(G_ExecDateTime.get(0));
            lastDateTime = df.format(G_ExecDateTime.get(G_ExecDateTime.size() - 1));
            quot = G_ExecDateTime.get(G_ExecDateTime.size() - 1).getTime() - G_ExecDateTime.get(0).getTime();
            timeDiff = String.valueOf(quot / 1000);
        }

        TOTAL_CNT = G_DONE_CNT + G_PASS_CNT + G_FAIL_CNT + G_WARNING_CNT + G_OTHERS_CNT;

        sLog = "<html><head><meta http-equiv='Content-Type' content='text/html; charset=utf-8'>";
        sLog = sLog + "<style type='text/css'>body,table{font-size:13px;}</style></head><body>";
        sLog = sLog + "<table border='0' width='60%'><tr><td>";

        // Execution result summary
        sLog = sLog + "<table border='1' width='80%' style='border-collapse:collapse;' bordercolor='#dddddd'>";

        sLog = sLog + "<tr><td><b>Model:</b></td><td>" + android.os.Build.MODEL + "</td></tr>";
        sLog = sLog + "<tr><td><b>Android SDK version:</b></td><td>" + android.os.Build.VERSION.SDK + "</td></tr>";
        sLog = sLog + "<tr><td><b>MSDK version:</b></td><td>" + SDKManager.get().getSDKVersion() + "</td></tr>";
        sLog = sLog + "<tr><td><b>OS version:</b></td><td>" + android.os.Build.VERSION.RELEASE + "</td></tr>";
        sLog = sLog + "<tr><td><b>Start time:</b></td><td>" + firstDateTime + "</td></tr>";
        sLog = sLog + "<tr><td><b>End time:</b></td><td>" + lastDateTime + "</td></tr>";
        sLog = sLog + "<tr><td><b>Duration:</b></td><td>" + timeDiff + "</td></tr>";
        sLog = sLog + "</table>";
        sLog = sLog + "</td><td>";

        // Operation counts
        sLog = sLog + "<table border='1' width='100%' style='border-collapse:collapse;' bordercolor='#dddddd'>";
        sLog = sLog + "<tr>";
        sLog = sLog + "<td bgcolor='#003366' width='15%'><font color='#FFFFFF'><b>Result</b></font></td>";
        sLog = sLog + "<td bgcolor='#003366' width='35%'><font color='#FFFFFF'><b>Count</b></font></td>";
        sLog = sLog + "</tr>";
        sLog = sLog + "<tr><td><b><font color='#000000'>DONE</font></b></td><td>" + G_DONE_CNT + "</td></tr>";
        sLog = sLog + "<tr><td><b><font color='#008000'>PASS</font></b></td><td>" + G_PASS_CNT + "</td></tr>";
        sLog = sLog + "<tr><td><b><font color='#ff0000'>FAIL</font></b></td><td>" + G_FAIL_CNT + "</td></tr>";
        sLog = sLog + "<tr><td><b><font color='#0000ff'>WARNING</font></b></td><td>" + G_WARNING_CNT + "</td></tr>";
        sLog = sLog + "<tr><td><b><font color='#969696'>OTHERS</font></b></td><td>" + G_OTHERS_CNT + "</td></tr>";
        sLog = sLog + "</table>";

        sLog = sLog + "</td></tr></table><br>";

        sLog = sLog + "<table border='1' width='30%' style='border-collapse:collapse;' bordercolor='#dddddd'>";
        sLog = sLog + "<tr><td bgcolor='#800000' ><font color='#FFFFFF'><b>Case Name</b></font></td></tr>";
        sLog = sLog + G_TCList;
        sLog = sLog + "</table><br>";

        // Operation and checkpoint details
        sLog = sLog + "<table border='1' width='100%' style='border-collapse:collapse;' bordercolor='#dddddd'>";
        sLog = sLog + "<tr>";
        sLog = sLog + "<td bgcolor='#003366' width='15%'><font color='#FFFFFF'><b>Time</b></font></td>";
        sLog = sLog + "<td bgcolor='#003366' width='20%'><font color='#FFFFFF'><b>Business</b></font></td>";
        sLog = sLog + "<td bgcolor='#003366' width='10%'><font color='#FFFFFF'><b>Result</b></font></td>";
        sLog = sLog + "<td bgcolor='#003366' width='40%'><font color='#FFFFFF'><b>Comment</b></font></td>";
        sLog = sLog + "<td bgcolor='#003366' width='15%'><font color='#FFFFFF'><b>Screenshot</b></font></td>";
        sLog = sLog + "</tr>";

        G_HTMLLog = sLog + G_HTMLLog + "</body></html>";

        String path = context.getExternalCacheDir().getAbsolutePath() + "/AutelTest/" + nowDateTime + "/";
        String finalPath = writeLog(path, G_HTMLLog);
        clearData();
        return finalPath;
    }

    private static void clearData() {
        G_HTMLLog = ""; // HTML Log
        G_TCList = ""; // executed test cases
        G_DONE_CNT = 0;
        G_PASS_CNT = 0;
        G_FAIL_CNT = 0;
        G_WARNING_CNT = 0;
        G_OTHERS_CNT = 0; // per-state counts of operation steps
        G_ExecDateTime.clear();
    }

    /**
     * Record the case name
     */
    public static void setTCNameLog(String tName) {
        tcName = tName;
        G_TCList = G_TCList + "<tr><td>" + tName + "</td></tr>";
    }

    public static String writeLog(String sFilePath, String sContent) {
        return saveFile(sFilePath, tcName+".html", sContent);
    }

    public static String saveFile(String filePath, String fileName, String content) {
        FileOutputStream outFile = null;
        OutputStreamWriter osw = null;
        filePathExists(filePath);
        String fileAbsPath = filePath + fileName;
        try {
            outFile = new FileOutputStream(fileAbsPath);
            osw = new OutputStreamWriter(outFile, StandardCharsets.UTF_8);
            osw.write(content);
            osw.flush();
        } catch (IOException e) {
            SDKLog.printSafeStackTrace(e);
        } finally {
            try {
                osw.close();
                outFile.close();
            } catch (IOException e) {
                SDKLog.printSafeStackTrace(e);
            }
        }
        return fileAbsPath;
    }

    public static void filePathExists(String path) {
        File f = new File(path);
        if (!f.exists()) {
            f.mkdirs();// create the directory if missing
        }
    }
}
