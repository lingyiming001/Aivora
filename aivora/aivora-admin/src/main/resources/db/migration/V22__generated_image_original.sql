alter table chat_image_attachment add column original_image bytea;
alter table chat_image_attachment add column original_media_type varchar(30);
