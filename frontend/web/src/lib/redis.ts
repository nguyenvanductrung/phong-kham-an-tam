import Redis from "ioredis";

const globalForRedis = globalThis as typeof globalThis & { clinicRedis?: Redis };

export const redis = globalForRedis.clinicRedis ?? new Redis({
  host: process.env.REDIS_HOST ?? "127.0.0.1",
  port: Number(process.env.REDIS_PORT ?? "6379"),
  password: process.env.REDIS_PASSWORD,
  db: Number(process.env.REDIS_DB ?? "1"),
  lazyConnect: true,
  maxRetriesPerRequest: 2,
});

if (process.env.NODE_ENV !== "production") globalForRedis.clinicRedis = redis;

