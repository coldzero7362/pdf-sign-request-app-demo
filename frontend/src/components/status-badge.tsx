import { STATUS_LABEL, type RequestStatus } from "@/lib/types";

export function StatusBadge({ status }: { status: RequestStatus }) {
  return <span className={`badge badge-${status.toLowerCase()}`}>{STATUS_LABEL[status]}</span>;
}
