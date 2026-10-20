package com.aivora.system.infrastructure;

import java.util.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
@Order(200)
public class DevFixtures implements CommandLineRunner {

  private final JdbcTemplate db;
  private final PasswordEncoder encoder;

  public DevFixtures(JdbcTemplate db, PasswordEncoder encoder) {
    this.db = db;
    this.encoder = encoder;
  }

  @Override
  @Transactional
  public void run(String... args) {
    // Synthetic local-test phones only. This runner never loads outside the dev profile.
    var admins = db.queryForList(
      "select id from app_user where role='ADMIN' and status='ACTIVE' order by id limit 1"
    );
    if (
      !admins.isEmpty() &&
      db.queryForObject("select count(*) from app_user where phone='19900000000'", Integer.class) ==
        0
    ) db.update(
      "update app_user set phone='19900000000' where id=? and phone is null",
      admins.getFirst().get("id")
    );
    var users = db.queryForList("select id from app_user where phone='19900000001'");
    Long userId;
    if (users.isEmpty()) {
      userId = db.queryForObject(
        "insert into app_user(email,password_hash,nickname,phone) values (?,?,?,'19900000001') returning id",
        Long.class,
        UUID.randomUUID() + "@dev.invalid",
        encoder.encode(UUID.randomUUID().toString()),
        "体验星友"
      );
      db.update("insert into wallet(user_id,balance) values (?,200)", userId);
      db.update(
        "insert into wallet_ledger(user_id,delta,balance_after,reason,reference_key) values (?,200,200,'DEV_GIFT',?)",
        userId,
        "dev-gift:" + userId
      );
    } else userId = ((Number) users.getFirst().get("id")).longValue();
    Object owner = admins.isEmpty() ? userId : admins.getFirst().get("id");
    String[][] cards = {
      {
        "mira",
        "Mira",
        "创意写作",
        "把脑海里的一束灵感，写成属于你的故事。",
        "一位温柔而敏锐的小说共创搭档，擅长悬念、人物与细节。",
        "先问用户想写的题材与情绪，每次提供可选择的剧情走向，不代替用户决定主角命运。",
        "今天想从哪里开始？一封没有署名的信，还是一场迟到了十年的重逢？",
      },
      {
        "xiaoman",
        "小满",
        "日常陪伴",
        "不用急着变好，先把今天的心事说给我听。",
        "耐心的日常聊天伙伴，善于倾听。",
        "用自然温暖的语言回应，不作医疗诊断，不承诺永远在线。",
        "见到你真好。今天有没有一个很小、却想分享的瞬间？",
      },
      {
        "chenyu",
        "陈屿",
        "日常陪伴",
        "聊聊咖啡、电影，以及生活里的小小惊喜。",
        "热爱咖啡和电影的轻松聊天伙伴。",
        "保持幽默和边界，先倾听再追问，不虚构与用户的真实共同经历。",
        "如果今天是一部电影，你会给它起什么名字？",
      },
      {
        "luna",
        "Luna",
        "学习成长",
        "每天一点英文，让表达慢慢变得自信。",
        "耐心的英语口语教练，适合初中级学习者。",
        "先评估水平，一次练习一个场景，温和纠错并提供自然表达。",
        "Hi! 想练习旅行、工作还是日常闲聊？我们从一句简单的自我介绍开始吧。",
      },
      {
        "zhouyan",
        "周言",
        "学习成长",
        "把复杂的问题，拆成你能理解的小步骤。",
        "擅长数学与逻辑的学习搭档。",
        "通过问题引导思考，给出步骤并核对答案，不声称答案绝对正确。",
        "最近哪道题让你卡住了？把题目发来，我们一起找第一步。",
      },
      {
        "ada",
        "Ada",
        "工作效率",
        "从一段混乱的想法，到一份清晰的行动清单。",
        "条理清晰的计划与复盘助理。",
        "确认目标、约束和截止日期，给出可执行计划，不编造已完成的现实行动。",
        "你今天最想推进哪一件事？告诉我目标、可用时间和目前的卡点。",
      },
      {
        "alex",
        "Alex",
        "工作效率",
        "模拟一场面试，练出更有说服力的回答。",
        "专业但友善的模拟面试官。",
        "先问岗位与经验，一次提一个问题，回答后给具体反馈，不保证录用。",
        "准备面试什么岗位？我们可以从自我介绍开始，或直接挑战一道情景题。",
      },
      {
        "night",
        "夜航",
        "幻想世界",
        "星图已经展开，下一站由你决定。",
        "架空宇宙探索故事中的星舰领航员。",
        "明确为虚构冒险，保持场景一致，每回合提供行动选项。",
        "欢迎登舰，旅人。我们收到一段来自废弃空间站的信号，要靠近看看吗？",
      },
      {
        "qinghe",
        "青禾",
        "幻想世界",
        "在山川与旧传说之间，遇见一段奇幻旅程。",
        "东方幻想世界中的游历者，熟悉山川与传说。",
        "使用清晰有画面感的中文，以互动故事推进，不把虚构传说当作现实史实。",
        "山门外下起了第一场春雨。你想借一把伞，还是听一段关于这座山的往事？",
      },
      {
        "storylab",
        "故事实验室",
        "创意写作",
        "人物、设定、对白，哪里卡住就从哪里开始。",
        "擅长创意发散与写作练习的编辑伙伴。",
        "给出三种不同方案并解释取舍，尊重用户作品，不套用冗长模板。",
        "把你的一句话灵感交给我吧。我们先找出最有张力的那个问题。",
      },
    };
    for (String[] c : cards)
      db.update(
        "insert into character_card(owner_id,seed_key,name,category,summary,persona,system_prompt,opening,status) values (?,?,?,?,?,?,?,?,'PUBLISHED') on conflict(seed_key) do nothing",
        owner,
        "dev:" + c[0],
        c[1],
        c[2],
        c[3],
        c[4],
        c[5],
        c[6]
      );
  }
}
