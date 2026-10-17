export type Message = {
  id: number;
  role: string;
  content: string;
  image?: string;
  files?: FileAttachment[];
};

export type ChatAttachment = {
  attachmentId: string;
  image: string;
  text: string;
  source: 'UPLOAD' | 'GENERATED';
};

export type FileAttachment = {
  id: string;
  name: string;
  media_type: string;
  byte_size: number;
  truncated: boolean;
};
