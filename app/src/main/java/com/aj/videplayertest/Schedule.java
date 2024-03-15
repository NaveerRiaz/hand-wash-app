package com.aj.videplayertest;

public class Schedule {
    private int start;
    private int end;
    private int timeout;
    private int length;

    public Schedule(int start,int end,int timeout,int length)
    {
        this.start = start;
        this.end = end;
        this.timeout = timeout;
        this.length = length;
    }
    public int getStart() {
        return start;
    }

    public void setStart(int start) {
        this.start = start;
    }

    public int getEnd() {
        return end;
    }

    public void setEnd(int end) {
        this.end = end;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }
}
