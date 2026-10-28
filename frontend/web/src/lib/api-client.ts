"use client";

import type { paths } from "@/lib/api.generated";

export type ApiProblem = { title?: string; detail?: string; errorCode?: string; fieldErrors?: Record<string, string> };

export type ApiPath = keyof paths & string;
export type ApiHttpMethod = "get" | "post" | "put" | "patch" | "delete";
export type ApiMethod<P extends ApiPath> = Extract<keyof paths[P], ApiHttpMethod>;
type ApiOperation<P extends ApiPath, M extends ApiMethod<P>> = NonNullable<paths[P][M]>;
type OperationParameters<Operation> = Operation extends { parameters?: infer Parameters } ? Parameters : never;
type QueryParameters<Operation> = OperationParameters<Operation> extends { query?: infer Query }
  ? [Query] extends [undefined] ? never : Query
  : never;
type HeaderParameters<Operation> = OperationParameters<Operation> extends { header?: infer Header }
  ? [Header] extends [undefined] ? never : Header
  : never;
type RequestBody<Operation> = Operation extends { requestBody: { content: { "application/json": infer Body } } } ? Body : never;
type JsonResponse<Operation> = Operation extends { responses: infer Responses }
  ? { [Status in keyof Responses]: Responses[Status] extends { content: { "application/json": infer Body } } ? Body : never }[keyof Responses]
  : never;
type PathParameterNames<Path extends string> = Path extends `${string}{${infer Name}}${infer Rest}` ? Name | PathParameterNames<Rest> : never;
type PathOption<Path extends string> = [PathParameterNames<Path>] extends [never]
  ? { path?: never }
  : { path: Record<PathParameterNames<Path>, string> };
type QueryOption<Operation> = [QueryParameters<Operation>] extends [never]
  ? { query?: never }
  : Record<never, never> extends QueryParameters<Operation>
    ? { query?: QueryParameters<Operation> }
    : { query: QueryParameters<Operation> };
type HeaderOption<Operation> = [HeaderParameters<Operation>] extends [never]
  ? { parameters?: never }
  : { parameters: { header: HeaderParameters<Operation> } };
type BodyOption<Operation> = Operation extends { requestBody: unknown }
  ? [RequestBody<Operation>] extends [never] ? { body?: never } : { body: RequestBody<Operation> }
  : { body?: never };

export type ApiOperationOptions<P extends ApiPath, M extends ApiMethod<P>> =
  PathOption<P> & QueryOption<ApiOperation<P, M>> & HeaderOption<ApiOperation<P, M>> & BodyOption<ApiOperation<P, M>> & { headers?: HeadersInit; signal?: AbortSignal };

export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string, public fieldErrors: Record<string, string> = {}) {
    super(message);
  }
}

export async function apiRequest<T>(path: string, csrfToken: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method ?? "GET").toUpperCase();
  const headers = new Headers(init.headers);
  if (init.body && !(init.body instanceof FormData) && !headers.has("content-type")) headers.set("content-type", "application/json");
  if (!["GET", "HEAD", "OPTIONS"].includes(method)) headers.set("x-csrf-token", csrfToken);
  const response = await fetch(`/api/bff${path}`, { ...init, method, headers, cache: "no-store" });
  if (!response.ok) {
    const problem = (await response.json().catch(() => ({}))) as ApiProblem;
    throw new ApiError(response.status, problem.errorCode ?? "REQUEST_FAILED", problem.detail ?? problem.title ?? "Không thể hoàn thành yêu cầu.", problem.fieldErrors ?? {});
  }
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

export async function apiOperation<P extends ApiPath, M extends ApiMethod<P>>(
  path: P,
  method: M,
  csrfToken: string,
  options: ApiOperationOptions<P, M>,
): Promise<JsonResponse<ApiOperation<P, M>>> {
  const pathValues = "path" in options && options.path ? options.path as Record<string, string> : {};
  const resolvedPath = path.replace(/\{([^}]+)\}/g, (_match, key: string) => {
    const value = pathValues[key];
    if (!value) throw new Error(`Missing OpenAPI path parameter: ${key}`);
    return encodeURIComponent(value);
  });
  const query = "query" in options && options.query ? options.query as Record<string, unknown> : undefined;
  const queryString = new URLSearchParams();
  for (const [key, value] of Object.entries(query ?? {})) {
    if (value !== undefined && value !== null) queryString.set(key, String(value));
  }
  const suffix = queryString.size ? `?${queryString.toString()}` : "";
  const headers = new Headers(options.headers);
  const declaredHeaders = "parameters" in options && options.parameters ? options.parameters.header as Record<string, string> : {};
  for (const [key, value] of Object.entries(declaredHeaders)) headers.set(key, value);
  const body = "body" in options ? options.body : undefined;
  return apiRequest<JsonResponse<ApiOperation<P, M>>>(`${resolvedPath}${suffix}`, csrfToken, {
    method: method.toUpperCase(),
    headers,
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    ...(options.signal ? { signal: options.signal } : {}),
  });
}

