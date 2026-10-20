create table character_engagement (
 user_id bigint not null references app_user(id),character_id bigint not null references character_card(id),
 favorite boolean not null default false,vote smallint not null default 0 check(vote in (-1,0,1)),updated_at timestamptz not null default now(),
 primary key(user_id,character_id)
);
create index idx_engagement_character on character_engagement(character_id);
alter table ai_model drop constraint ai_model_model_type_check;
alter table ai_model add constraint ai_model_model_type_check check(model_type in ('TEXT','CHAT','VOICE','ASR','TTS','IMAGE','MULTIMODAL'));
insert into ai_model(provider_id,model_name,model_type,enabled,star_per_request,capabilities)
select p.id,v.name,v.type,p.enabled and p.api_key_ciphertext is not null,v.stars,v.type from ai_provider p cross join (values ('qwen3-asr-flash','ASR',0),('qwen3-tts-flash','TTS',0),('qwen3-vl-plus','MULTIMODAL',0),('qwen-image-2.0','IMAGE',3)) v(name,type,stars)
where p.base_url='https://dashscope.aliyuncs.com/compatible-mode/v1' and p.kind='OPENAI_COMPATIBLE'
on conflict(provider_id,model_name) do nothing;
