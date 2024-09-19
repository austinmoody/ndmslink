package com.lantanagroup.link.tasks.helpers;

import lombok.Getter;
import lombok.Setter;
import org.springframework.context.annotation.Configuration;

import java.util.Calendar;

@Getter
@Setter
@Configuration
public class StartDateAdjuster extends DateAdjuster {

    public StartDateAdjuster() {
        super();
    }

    public StartDateAdjuster(int adjustDays, int adjustMonths, boolean dateEdge) {
        super(adjustDays, adjustMonths, dateEdge);
    }

    protected void SetDateEdge() {
        super.SetDateEdge();
        if (dateEdge) {
            calendar.set(Calendar.MILLISECOND, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.HOUR_OF_DAY, 0);
        }
    }
}