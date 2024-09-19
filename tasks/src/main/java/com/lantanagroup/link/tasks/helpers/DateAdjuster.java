package com.lantanagroup.link.tasks.helpers;

import lombok.Getter;
import lombok.Setter;
import org.springframework.context.annotation.Configuration;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

@Getter
@Setter
@Configuration
public class DateAdjuster {

    protected int adjustDays;
    protected int adjustMonths;
    protected boolean dateEdge;
    protected Calendar calendar;

    public DateAdjuster() {}

    public DateAdjuster(int adjustDays, int adjustMonths, boolean dateEdge) {
        this.adjustDays = adjustDays;
        this.adjustMonths = adjustMonths;
        this.dateEdge = dateEdge;
    }

    public Date Date() {
        setCalendar();
        return calendar.getTime();
    }

    protected void SetDateEdge() {

    }

    private void setCalendar() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        this.calendar = new GregorianCalendar();
        this.calendar.add(Calendar.HOUR, (adjustDays * 24));
        this.calendar.add(Calendar.MONTH, adjustMonths);
        SetDateEdge();
    }
}