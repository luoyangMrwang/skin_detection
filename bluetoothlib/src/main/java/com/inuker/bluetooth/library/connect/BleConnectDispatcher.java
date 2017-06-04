package com.inuker.bluetooth.library.connect;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.inuker.bluetooth.library.Code;
import com.inuker.bluetooth.library.RuntimeChecker;
import com.inuker.bluetooth.library.connect.options.BleConnectOptions;
import com.inuker.bluetooth.library.connect.request.BleConnectRequest;
import com.inuker.bluetooth.library.connect.request.BleIndicateRequest;
import com.inuker.bluetooth.library.connect.request.BleNotifyRequest;
import com.inuker.bluetooth.library.connect.request.BleReadRequest;
import com.inuker.bluetooth.library.connect.request.BleReadRssiRequest;
import com.inuker.bluetooth.library.connect.request.BleRequest;
import com.inuker.bluetooth.library.connect.request.BleUnnotifyRequest;
import com.inuker.bluetooth.library.connect.request.BleWriteNoRspRequest;
import com.inuker.bluetooth.library.connect.request.BleWriteRequest;
import com.inuker.bluetooth.library.connect.response.BleGeneralResponse;
import com.inuker.bluetooth.library.utils.BluetoothLog;
import com.inuker.bluetooth.library.utils.ListUtils;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

public class BleConnectDispatcher implements IBleConnectDispatcher, RuntimeChecker, Handler.Callback {

    private static final int MAX_REQUEST_COUNT = 100;
    private static final int MSG_SCHEDULE_NEXT = 0x12;

    private List<BleRequest> mBleWorkList;
    private BleRequest mCurrentRequest;

    private IBleConnectWorker mWorker;

    private String mAddress;

    private Handler mHandler;

    public static BleConnectDispatcher newInstance(String mac) {
        return new BleConnectDispatcher(mac);
    }

    private BleConnectDispatcher(String mac) {
        mAddress = mac;
        mBleWorkList = new LinkedList<BleRequest>();
        mWorker = new BleConnectWorker(mac, this);
        mHandler = new Handler(Looper.myLooper(), this);
    }

    public void connect(BleConnectOptions options, BleGeneralResponse response) {
        addNewRequest(new BleConnectRequest(options, response));
    }

    public void disconnect() {
        checkRuntime();

        BluetoothLog.w(String.format("start process disconnect"));

        if (mCurrentRequest != null) {
            mCurrentRequest.cancel();
            mCurrentRequest = null;
        }

        for (BleRequest request : mBleWorkList) {
            request.cancel();
        }

        mBleWorkList.clear();

        mWorker.closeGatt();
    }

    public void read(UUID service, UUID character, BleGeneralResponse response) {
        addNewRequest(new BleReadRequest(service, character, response));
    }

    public void write(UUID service, UUID character, byte[] bytes, BleGeneralResponse response) {
        addNewRequest(new BleWriteRequest(service, character, bytes, response));
    }

    public void writeNoRsp(UUID service, UUID character, byte[] bytes, BleGeneralResponse response) {
        addNewRequest(new BleWriteNoRspRequest(service, character, bytes, response));
    }

    public void notify(UUID service, UUID character, BleGeneralResponse response) {
        addNewRequest(new BleNotifyRequest(service, character, response));
    }

    public void unnotify(UUID service, UUID character, BleGeneralResponse response) {
        addNewRequest(new BleUnnotifyRequest(service, character, response));
    }

    public void indicate(UUID service, UUID character, BleGeneralResponse response) {
        addNewRequest(new BleIndicateRequest(service, character, response));
    }

    public void unindicate(UUID service, UUID character, BleGeneralResponse response) {
        addNewRequest(new BleUnnotifyRequest(service, character, response));
    }

    public void readRemoteRssi(BleGeneralResponse response) {
        addNewRequest(new BleReadRssiRequest(response));
    }

    private void addNewRequest(BleRequest request) {
        checkRuntime();

        if (mBleWorkList.size() < MAX_REQUEST_COUNT) {
            request.setRuntimeChecker(this);
            request.setAddress(mAddress);
            request.setWorker(mWorker);
            mBleWorkList.add(request);
        } else {
            request.onResponse(Code.REQUEST_OVERFLOW);
        }

        scheduleNextRequest(100);
    }

    @Override
    public void onRequestCompleted(BleRequest request) {
        checkRuntime();

        if (request != mCurrentRequest) {
            throw new IllegalStateException("request not match");
        }

        mCurrentRequest = null;

        scheduleNextRequest(100);
    }

    private void scheduleNextRequest(long delayInMillis) {
        mHandler.sendEmptyMessageDelayed(MSG_SCHEDULE_NEXT, delayInMillis);
    }

    private void scheduleNextRequest() {
        if (mCurrentRequest != null) {
            return;
        }

        if (!ListUtils.isEmpty(mBleWorkList)) {
            mCurrentRequest = mBleWorkList.remove(0);
            mCurrentRequest.process(this);
        }
    }

    @Override
    public void checkRuntime() {
        if (Thread.currentThread() != mHandler.getLooper().getThread()) {
            throw new IllegalStateException("Thread Context Illegal");
        }
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_SCHEDULE_NEXT:
                scheduleNextRequest();
                break;
        }
        return true;
    }
}