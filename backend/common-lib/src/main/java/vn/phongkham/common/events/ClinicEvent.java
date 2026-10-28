package vn.phongkham.common.events;

import tools.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record ClinicEvent(UUID eventId,String eventType,int schemaVersion,Instant occurredAt,UUID aggregateId,
                         long aggregateVersion,String correlationId,JsonNode payload) {}

