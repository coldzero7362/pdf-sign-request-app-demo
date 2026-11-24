import { STATUS_LABEL, type DocumentStatus } from "@/lib/types";

export function StatusBadge({ status }: { status: DocumentStatus }) {
  return <span className={`badge badge-${status.toLowerCase()}`}>{STATUS_LABEL[status]}</span>;
}
