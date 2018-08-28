package com.kingsly.mtpdemo;

import java.io.Serializable;

public class Command implements Serializable{

    private int mCommandType;
    private int mCommandId;
    private String mCommandContent;

    public Command() {

    }
    public int getCommandType() {
        return mCommandType;
    }

    public void setCommandType(int commandType) {
        this.mCommandType = commandType;
    }

    public int getCommandID() {
        return mCommandId;
    }

    public void setCommandID(int commandId) {
        this.mCommandId = commandId;
    }

    public String getCommandContent() {
        return mCommandContent;
    }

    public void setCommandContent(String commandContent) {
        this.mCommandContent = commandContent;
    }
}
