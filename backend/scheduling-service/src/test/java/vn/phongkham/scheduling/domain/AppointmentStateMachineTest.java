package vn.phongkham.scheduling.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import vn.phongkham.common.ApiException;
import vn.phongkham.scheduling.api.SchedulingModels.AppointmentCommand.Command;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class AppointmentStateMachineTest {
    private final AppointmentStateMachine machine=new AppointmentStateMachine();
    private static final Instant START=Instant.parse("2030-01-01T10:00:00Z");

    static Stream<Object[]> validTransitions(){
        return Stream.of(
                new Object[]{"BOOKED",Command.CONFIRM,Set.of("RECEPTIONIST"),"CONFIRMED"},
                new Object[]{"BOOKED",Command.CONFIRM,Set.of("CLINIC_MANAGER"),"CONFIRMED"},
                new Object[]{"CONFIRMED",Command.CHECK_IN,Set.of("RECEPTIONIST"),"ARRIVED"},
                new Object[]{"CONFIRMED",Command.CHECK_IN,Set.of("ASSISTANT"),"ARRIVED"},
                new Object[]{"ARRIVED",Command.START,Set.of("DOCTOR"),"IN_PROGRESS"},
                new Object[]{"BOOKED",Command.CANCEL,Set.of("PATIENT"),"CANCELLED"},
                new Object[]{"CONFIRMED",Command.CANCEL,Set.of("RECEPTIONIST"),"CANCELLED"},
                new Object[]{"BOOKED",Command.MARK_NO_SHOW,Set.of("CLINIC_MANAGER"),"NO_SHOW"}
        );
    }
    @ParameterizedTest @MethodSource("validTransitions")
    void allowsOnlyDocumentedTransitions(String current,Command command,Set<String> roles,String expected){
        assertEquals(expected,machine.transition(current,command,roles,START,15,START.plusSeconds(15*60L)));
    }

    static Stream<Object[]> invalidTransitions(){
        return Stream.of(
                new Object[]{"BOOKED",Command.CHECK_IN,Set.of("RECEPTIONIST"),START.plusSeconds(3600)},
                new Object[]{"CONFIRMED",Command.CONFIRM,Set.of("RECEPTIONIST"),START.plusSeconds(3600)},
                new Object[]{"ARRIVED",Command.CONFIRM,Set.of("CLINIC_MANAGER"),START.plusSeconds(3600)},
                new Object[]{"BOOKED",Command.CONFIRM,Set.of("DOCTOR"),START.plusSeconds(3600)},
                new Object[]{"ARRIVED",Command.START,Set.of("ASSISTANT"),START.plusSeconds(3600)},
                new Object[]{"COMPLETED",Command.CANCEL,Set.of("SUPER_ADMIN"),START.plusSeconds(3600)},
                new Object[]{"IN_PROGRESS",Command.START,Set.of("DOCTOR"),START.plusSeconds(3600)},
                new Object[]{"BOOKED",Command.MARK_NO_SHOW,Set.of("RECEPTIONIST"),START.plusSeconds(14*60L)},
                new Object[]{"CANCELLED",Command.MARK_NO_SHOW,Set.of("CLINIC_MANAGER"),START.plusSeconds(3600)}
        );
    }
    @ParameterizedTest @MethodSource("invalidTransitions")
    void rejectsInvalidStateRoleAndTimeCells(String current,Command command,Set<String> roles,Instant now){
        assertThrows(ApiException.class,()->machine.transition(current,command,roles,START,15,now));
    }
}

