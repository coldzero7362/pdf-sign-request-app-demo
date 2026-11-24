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

export type DocumentStatus = "IN_PROGRESS" | "COMPLETED" | "REJECTED" | "RECALLED" | "EXPIRED";
export type RecipientStatus = "WAITING" | "REQUESTED" | "VIEWED" | "SIGNED" | "REJECTED" | "NOTIFIED";
export type RecipientRole = "SIGNER" | "VIEWER" | "OWNER";
export type FieldType = "TEXT" | "DATE" | "CHECKBOX" | "SIGNATURE" | "TEXT_FIXED";

export type RecipientView = {
  id: string;
  role: RecipientRole;
  slot: string | null;
  slotLabel: string | null;
  order: number;
  email: string;
  name: string;
  status: RecipientStatus;
  requestedAt: string | null;
  viewedAt: string | null;
  signedAt: string | null;
  rejectedAt: string | null;
  rejectReason: string | null;
};

export type DocumentFieldView = {
  id: string;
  slot: string | null;
  type: FieldType;
  page: number;
  x: number;
  y: number;
  width: number;
  height: number;
  required: boolean;
  label: string | null;
  fixedValue: string | null;
  value: string | null;
  hasFile: boolean;
  filledAt: string | null;
};

export type AuditView = { at: string; type: string; actor: string; detail: string | null; ip: string | null; relatedDocumentId: string | null };

export type LinkedDocument = { id: string; title: string; status: DocumentStatus; relation: "PREVIOUS" | "NEXT" };

export type DocumentDetail = {
  id: string;
  title: string;
  message: string | null;
  status: DocumentStatus;
  templateNo: string;
  templateRevision: number;
  signingMode: "SEQUENTIAL" | "PARALLEL";
  periodDays: number;
  sentAt: string;
  expiresAt: string;
  completedAt: string | null;
  rejectedAt: string | null;
  recalledAt: string | null;
  expiredAt: string | null;
  hiddenAt: string | null;
  pdfFileName: string;
  pageCount: number;
  pages: PageView[];
  resultSha256: string | null;
  resultSizeBytes: number | null;
  signers: RecipientView[];
  viewers: RecipientView[];
  fields: DocumentFieldView[];
  audit: AuditView[];
  linked: LinkedDocument[];
  canRecall: boolean;
};

export type DocumentSummary = {
  id: string;
  title: string;
  status: DocumentStatus;
  templateNo: string;
  signingMode: "SEQUENTIAL" | "PARALLEL";
  signerCount: number;
  signedCount: number;
  currentSigner: string | null;
  sentAt: string;
  expiresAt: string;
  completedAt: string | null;
  hidden: boolean;
};

export const STATUS_LABEL: Record<DocumentStatus, string> = {
  IN_PROGRESS: "진행 중",
  COMPLETED: "완료",
  REJECTED: "반려",
  RECALLED: "회수",
  EXPIRED: "만료",
};

export const RECIPIENT_STATUS_LABEL: Record<RecipientStatus, string> = {
  WAITING: "차례 대기",
  REQUESTED: "요청됨",
  VIEWED: "열람함",
  SIGNED: "서명 완료",
  REJECTED: "반려",
  NOTIFIED: "완료본 받음",
};

export const AUDIT_LABEL: Record<string, string> = {
  SENT: "발송",
  REQUESTED: "서명 요청 메일",
  REMINDED: "요청 메일 다시 보냄",
  OTP_SENT: "인증번호 발송",
  OTP_VERIFIED: "본인 확인",
  OTP_FAILED: "인증 실패",
  VIEWED: "문서 열람",
  SIGNED: "서명 완료",
  REJECTED: "반려",
  RECALLED: "회수",
  EXPIRED: "만료",
  COMPLETED: "전체 완료",
  RESULT_SENT: "완료 링크 발송",
};

export const FIELD_LABEL: Record<FieldType, string> = {
  TEXT: "텍스트",
  DATE: "날짜",
  CHECKBOX: "체크",
  SIGNATURE: "서명",
  TEXT_FIXED: "고정 텍스트",
};

export const MIN_FIELD_SIZE = 8;

/** 수신자 구분 색 (순서대로 돌려 쓴다). 앞 네 가지는 USIGN 팔레트, 뒤는 5명 이상일 때 구분용 */
export const RECIPIENT_COLORS = ["#1560FD", "#FFA019", "#2FC673", "#181A29", "#808087", "#0B7285"];

export const fmtDate = (iso: string | null | undefined) => (iso ? new Date(iso).toLocaleString("ko-KR") : "-");

// ---------- 템플릿 (backend TemplateDtos) ----------

export type SigningMode = "SEQUENTIAL" | "PARALLEL";
export type TemplateFieldType = "SIGNATURE" | "DATE" | "TEXT" | "TEXT_FIXED" | "CHECKBOX";

export type TemplateSlotView = { key: string; label: string; order: number; fieldCount: number };

export type TemplateFieldView = {
  id: string;
  slotKey: string | null;
  type: TemplateFieldType;
  page: number;
  x: number;
  y: number;
  width: number;
  height: number;
  required: boolean;
  label: string | null;
  fixedValue: string | null;
};

export type TemplateView = {
  templateNo: string;
  name: string;
  signingMode: SigningMode;
  defaultPeriodDays: number;
  revision: number;
  ready: boolean;
  problems: string[];
  pdf: { fileName: string; sizeBytes: number; sha256: string; pageCount: number; pages: PageView[] };
  slots: TemplateSlotView[];
  fields: TemplateFieldView[];
  requestExample: Record<string, unknown>;
  createdAt: string;
  updatedAt: string;
};

export type TemplateSummary = {
  templateNo: string;
  name: string;
  signingMode: SigningMode;
  defaultPeriodDays: number;
  slotCount: number;
  fieldCount: number;
  ready: boolean;
  revision: number;
  createdAt: string;
  updatedAt: string;
};

export const SIGNING_MODE_LABEL: Record<SigningMode, string> = {
  SEQUENTIAL: "순차",
  PARALLEL: "비순차",
};

export const TEMPLATE_FIELD_LABEL: Record<TemplateFieldType, string> = {
  SIGNATURE: "서명",
  TEXT: "텍스트",
  DATE: "날짜",
  CHECKBOX: "체크",
  TEXT_FIXED: "고정 텍스트",
};

export const TEMPLATE_FIELD_SIZE: Record<TemplateFieldType, { width: number; height: number }> = {
  SIGNATURE: { width: 150, height: 50 },
  TEXT: { width: 160, height: 24 },
  DATE: { width: 100, height: 24 },
  CHECKBOX: { width: 16, height: 16 },
  TEXT_FIXED: { width: 200, height: 24 },
};

/** 요청자 고정 텍스트 칸 색 (USIGN Muted) */
export const FIXED_FIELD_COLOR = "#808087";
