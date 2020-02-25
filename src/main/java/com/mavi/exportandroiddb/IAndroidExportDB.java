package com.mavi.exportandroiddb;

public interface IAndroidExportDB {

    void export(Callback callback);

    interface Callback {

        void onExported();

        void onFailure(Throwable t);

    }

}
