"use client";

import { useCallback } from "react";
import { apiOperation, apiRequest, type ApiMethod, type ApiOperationOptions, type ApiPath } from "@/lib/api-client";
import type { paths } from "@/lib/api.generated";
import { useSession } from "@/components/session-context";

type OperationFor<P extends ApiPath, M extends ApiMethod<P>> = NonNullable<paths[P][M]>;
type JsonResponse<Operation> = Operation extends { responses: infer Responses }
  ? { [Status in keyof Responses]: Responses[Status] extends { content: { "application/json": infer Body } } ? Body : never }[keyof Responses]
  : never;

export function useApi(){
  const {csrfToken}=useSession();
  const request=useCallback(<T,>(path:string,init?:RequestInit)=>apiRequest<T>(path,csrfToken,init),[csrfToken]);
  const operation=useCallback(<P extends ApiPath,M extends ApiMethod<P>>(path:P,method:M,options:ApiOperationOptions<P,M>)=>
    apiOperation(path,method,csrfToken,options) as Promise<JsonResponse<OperationFor<P,M>>>,[csrfToken]);
  return Object.assign(request,{operation});
}

