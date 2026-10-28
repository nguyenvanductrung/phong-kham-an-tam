package vn.phongkham.scheduling.domain;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vn.phongkham.common.ApiException;
import vn.phongkham.scheduling.api.SchedulingModels.AppointmentCommand.Command;

import java.time.Instant;
import java.util.Set;

@Component
public class AppointmentStateMachine {
    public String transition(String current, Command command, Set<String> roles, Instant startsAt, int noShowGraceMinutes, Instant now) {
        return switch (command) {
            case CONFIRM -> allowed(current.equals("BOOKED") && has(roles,"RECEPTIONIST","CLINIC_MANAGER","SUPER_ADMIN"), "CONFIRMED");
            case CHECK_IN -> allowed(current.equals("CONFIRMED") && has(roles,"RECEPTIONIST","ASSISTANT","CLINIC_MANAGER","SUPER_ADMIN"), "ARRIVED");
            case START -> allowed(current.equals("ARRIVED") && has(roles,"DOCTOR"), "IN_PROGRESS");
            case CANCEL -> allowed((current.equals("BOOKED") || current.equals("CONFIRMED"))
                    && has(roles,"PATIENT","RECEPTIONIST","CLINIC_MANAGER","SUPER_ADMIN"), "CANCELLED");
            case MARK_NO_SHOW -> allowed((current.equals("BOOKED") || current.equals("CONFIRMED"))
                    && has(roles,"RECEPTIONIST","CLINIC_MANAGER","SUPER_ADMIN")
                    && !now.isBefore(startsAt.plusSeconds(noShowGraceMinutes * 60L)), "NO_SHOW");
        };
    }
    private String allowed(boolean condition, String target) {
        if (!condition) throw new ApiException(HttpStatus.CONFLICT, "INVALID_APPOINTMENT_TRANSITION", "Trạng thái hoặc quyền hiện tại không cho phép thao tác này.");
        return target;
    }
    private static boolean has(Set<String> roles, String... accepted) {
        for (String role: accepted) if (roles.contains(role)) return true;
        return false;
    }
}

