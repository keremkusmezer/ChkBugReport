package com.sonyericsson.chkbugreport.plugins.logs.kernel;

import com.sonyericsson.chkbugreport.BugReportModule;
import com.sonyericsson.chkbugreport.Section;
import com.sonyericsson.chkbugreport.doc.Block;
import com.sonyericsson.chkbugreport.doc.Bug;
import com.sonyericsson.chkbugreport.doc.Chapter;
import com.sonyericsson.chkbugreport.doc.DocNode;
import com.sonyericsson.chkbugreport.doc.Hint;
import com.sonyericsson.chkbugreport.doc.Link;
import com.sonyericsson.chkbugreport.plugins.logs.LogToolbar;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogData {

    private static final Pattern SELECT_TO_KILL = Pattern.compile(".*] select (\\d+) .*, to kill$");
    private static final Pattern SEND_SIGKILL = Pattern.compile(".*] send sigkill to (\\d+) .*");
    private static final Pattern BINDER_RELEASE_NOT_FREED = Pattern.compile(".*] binder: release proc (\\d+)" +
            ", transaction (\\d+), not freed$");

    private Chapter mCh;
    private boolean mLoaded = false;
    private KernelLogLines mParsedLog = new KernelLogLines();
    private int mUnparsed;
    private PMStats mPMStats;
    private String mId;
    private BugReportModule mMod;
    private String mInfoId;
    KernelLogLine mPrev = null;

    public LogData(BugReportModule mod, Section section, String chapterName, String id, String infoId) {
        mMod = mod;
        mId = id;
        mInfoId = infoId;
        mPMStats = new PMStats(this, mod);
        mCh = new Chapter(mod, chapterName);

        if (section != null) {
            // Load and parse the lines
            int cnt = section.getLineCount();
            for (int i = 0; i < cnt; i++) {
                addLine(mod, section.getLine(i), -1);
            }
        }
    }

    protected void addLine(BugReportModule mod, String line, long realTs) {
        KernelLogLine kl = new KernelLogLine(mod, line, mPrev, realTs);
        if (kl.ok) {
            mParsedLog.add(kl);
        } else {
            mUnparsed++;
        }
    }

    public String getId() {
        return mId;
    }

    public boolean finishLoad() {
        int cnt = mParsedLog.size();

        // Annotate the log
        for (int i = 0; i < cnt; i++) {
            KernelLogLine kl = mParsedLog.get(i);
            annotate(kl, mMod, i);
        }

        // Analyze the log
        for (int i = 0; i < cnt;) {
            KernelLogLine kl = mParsedLog.get(i);
            i += analyze(kl, i, mMod);
        }

        mPMStats.load();

        // Load successful
        mLoaded = true;

        mMod.addInfo(mInfoId, mParsedLog);

        return mLoaded;
    }

    public int getLineCount() {
        return mParsedLog.size();
    }

    public KernelLogLine getLine(int i) {
        return mParsedLog.get(i);
    }

    public void generate(BugReportModule br) {
        if (!mLoaded) {
            return;
        }

        if (mUnparsed > 0) {
            new Hint(mCh).add("NOTE: " + mUnparsed + " lines were not parsed");
        }

        br.addChapter(mCh);
        generateLog(br);
        mPMStats.generate(br, mCh);
        new DeepSleepDetector(this, mMod, mParsedLog).run();
    }

    private void generateLog(BugReportModule br) {
        Chapter ch = new Chapter(br, "Log");
        mCh.addChapter(ch);
        new LogToolbar(ch);
        DocNode log = new Block(ch).addStyle("log");
        int cnt = mParsedLog.size();
        for (int i = 0; i < cnt; i++) {
            KernelLogLine kl = mParsedLog.get(i);
            log.add(kl);
        }
    }

    /**
     * Analyze a log line to see if it can be annotated with further information, such as links to
     * referenced data.
     */
    private void annotate(KernelLogLine kl, BugReportModule br, int i) {
        annotateSelectToKill(kl, br);
        annotateSendSigkill(kl, br);
        annotateBinderReleaseNotFreed(kl, br);
    }

    /**
     * Link the pid to its process record in a "select to kill" line.
     *
     * select 13814 (android.support), adj 8, size 5977, to kill
     */
    private void annotateSelectToKill(KernelLogLine kl, BugReportModule br) {
        Matcher matcher = SELECT_TO_KILL.matcher(kl.line);
        if (!matcher.matches()) {
            return;
        }

        int start = matcher.start(1);
        int end = matcher.end(1);
        kl.markPid(start, end);
    }

    /**
     * Link the pid to its process record in a "send sigkill" line.
     *
     * send sigkill to 8506 (et.digitalclock), adj 10, size 5498
     */
    private void annotateSendSigkill(KernelLogLine kl, BugReportModule br) {
        Matcher matcher = SEND_SIGKILL.matcher(kl.line);
        if (!matcher.matches()) {
            return;
        }

        int start = matcher.start(1);
        int end = matcher.end(1);
        kl.markPid(start, end);
    }

    /**
     * Link the pid to its process record in a "binder: release not freed" line.
     *
     * binder: release proc 9107, transaction 1076363, not freed
     */
    private void annotateBinderReleaseNotFreed(KernelLogLine kl, BugReportModule br) {
        Matcher matcher = BINDER_RELEASE_NOT_FREED.matcher(kl.line);
        if (!matcher.matches()) {
            return;
        }

        int start = matcher.start(1);
        int end = matcher.end(1);
        kl.markPid(start, end);
    }

    /**
     * Analyze the log starting from line 'i' to see if it contain any
     * information that will result in a Bug.
     *
     * Return the number of lines consumed.
     */
    private int analyze(KernelLogLine kl, int i, BugReportModule br) {
        int inc = analyzeFatal(kl, br, i);
        if (inc > 0) return inc;


        return 1;
    }

    /**
     * Generate a Bug for each block of log lines with a level of 1 or 2.
     */
    private int analyzeFatal(KernelLogLine kl, BugReportModule br, int i) {
        // Put a marker box
        String anchor = "kernel_log_fe_" + i;
        String type;
        int level = kl.getLevel();
        switch (level) {
            case 1:
                type = "EMERGENCY";
                break;
            case 2:
                type = "ALERT";
                break;
            default:
                return 1;
        }
        kl.addMarker("log-float-err", null, "<a name=\"" + anchor + "\">KERNEL<br/>" + type
                + "</a>", "KERNEL " + type);

        // Create a bug and store the relevant log lines
        Bug bug = new Bug(Bug.PRIO_ALERT_KERNEL_LOG, kl.ts, "KERNEL " + type);
        new Block(bug).add(new Link(kl.getAnchor(), "(link to log)"));
        DocNode log = new Block(bug).addStyle("log");
        log.add(kl.copy());
        int end = i + 1;
        while (end < mParsedLog.size()) {
            KernelLogLine extra = mParsedLog.get(end);
            if (extra.getLevel() != level)
                break;
            log.add(extra.copy());
            end++;
        }
        bug.setAttr(Bug.ATTR_FIRST_LINE, i);
        bug.setAttr(Bug.ATTR_LAST_LINE, end);
        bug.setAttr(Bug.ATTR_LOG_INFO_ID, mInfoId);
        br.addBug(bug);

        return end - i;
    }

    public void addChapter(Chapter ch) {
        mCh.addChapter(ch);
    }

}
