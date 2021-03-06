package com.sonyericsson.chkbugreport.plugins.logs.event;

public class NetstatSample {

    public static final int IDX_DEV_RX_BYTES    = 0;
    public static final int IDX_DEV_TX_BYTES    = 1;
    public static final int IDX_DEV_RX_PACKETS  = 2;
    public static final int IDX_DEV_TX_PACKETS  = 3;
    public static final int IDX_XT_RX_BYTES     = 4;
    public static final int IDX_XT_TX_BYTES     = 5;
    public static final int IDX_XT_RX_PACKETS   = 6;
    public static final int IDX_XT_TX_PACKETS   = 7;
    public static final int IDX_UID_RX_BYTES    = 8;
    public static final int IDX_UID_TX_BYTES    = 9;
    public static final int IDX_UID_RX_PACKETS  = 10;
    public static final int IDX_UID_TX_PACKETS  = 11;

    private String mType;
    private long[] mData = new long[12];
    private long mTs;

    public NetstatSample(String type, long ts, String[] data) {
        mType = type;
        mTs = ts;
        for (int i = 0; i < mData.length; i++) {
            mData[i] = Long.parseLong(data[i]);
        }
    }

    public String getType() {
        return mType;
    }

    public long getTs() {
        return mTs;
    }

    public long getData(int idx) {
        return mData[idx];
    }

}
