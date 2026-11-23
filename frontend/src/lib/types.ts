// 백엔드 API 응답 형식 (backend RequestDtos, DocumentController 참고)

export type PageView = { pageNo: number; width: number; height: number; rotation: number };

export type DocumentView = {
  id: string;
  fileName: string;
  sizeBytes: number;
  sha256: string;
  pageCount: number;
  pages: PageView[];
  createdAt: string;
};

export type RequestStatus = "DRAFT" | "SENT" | "IN_PROGRESS" | "COMPLETED" | "DECLINED" | "CANCELED" | "EXPIRED";
export type RecipientRole = "SIGNER" | "VIEWER";
export type RecipientStatus = "PENDING" | "SENT" | "VIEWED" | "COMPLETED" | "DECLINED";
export type FieldType = "TEXT" | "DATE" | "CHECKBOX" | "SIGNATURE";

export type RecipientView = {
  id: string;
  email: string;
  name: string;
  role: RecipientRole;
  status: RecipientStatus;
  order: number;
  completedAt: string | null;
  declinedAt: string | null;
  declineReason: string | null;
};

export type FieldView = {
  id: string;
  recipientId: string;
  type: FieldType;
  page: number;
  x: number;
  y: number;
  width: number;
  height: number;
  required: boolean;
  label: string | null;
  textValue: string | null;
  hasFile: boolean;
  filledAt: string | null;
};

export type RequestDetail = {
  id: string;
  title: string;
  message: string | null;
  status: RequestStatus;
  expiresAt: string | null;
  createdAt: string;
  updatedAt: string;
  sentAt: string | null;
  completedAt: string | null;
  canceledAt: string | null;
  document: DocumentView;
  recipients: RecipientView[];
  fields: FieldView[];
};

export type RequestSummary = {
  id: string;
  title: string;
  status: RequestStatus;
  documentName: string;
  signerCount: number;
  completedCount: number;
  createdAt: string;
  sentAt: string | null;
  expiresAt: string | null;
};

export type AuditView = { at: string; type: string; actor: string; detail: string | null; ip: string | null };

export const STATUS_LABEL: Record<RequestStatus, string> = {
  DRAFT: "작성 중",
  SENT: "발송됨",
  IN_PROGRESS: "진행 중",
  COMPLETED: "완료",
  DECLINED: "거절됨",
  CANCELED: "취소됨",
  EXPIRED: "만료됨",
};

export const RECIPIENT_STATUS_LABEL: Record<RecipientStatus, string> = {
  PENDING: "대기",
  SENT: "발송됨",
  VIEWED: "열람함",
  COMPLETED: "완료",
  DECLINED: "거절",
};

export const FIELD_LABEL: Record<FieldType, string> = {
  TEXT: "텍스트",
  DATE: "날짜",
  CHECKBOX: "체크",
  SIGNATURE: "서명",
};

/** 칸 종류별 기본 크기 (pt) */
export const FIELD_DEFAULT_SIZE: Record<FieldType, { width: number; height: number }> = {
  TEXT: { width: 160, height: 24 },
  DATE: { width: 100, height: 24 },
  CHECKBOX: { width: 16, height: 16 },
  SIGNATURE: { width: 150, height: 50 },
};

export const MIN_FIELD_SIZE = 8;

/** 수신자 구분 색 (순서대로 돌려 쓴다) */
export const RECIPIENT_COLORS = ["#2f5bea", "#d9480f", "#2b8a3e", "#ae3ec9", "#0b7285", "#e67700"];

export const fmtDate = (iso: string | null | undefined) => (iso ? new Date(iso).toLocaleString("ko-KR") : "-");
