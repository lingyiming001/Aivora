package com.aivora.chat.service;

import com.aivora.ai.domain.AiGateway;
import java.util.*;

/** Builds the same bounded conversation context for both main and helper models. */
public final class ChatPromptBuilder {

  public static final int MAX_HISTORY_CHARACTERS = 48000;

  private ChatPromptBuilder() {}

  public static String system(Map<String, Object> card) {
    return """
    你是通过文字消息与用户日常聊天的伙伴，以下角色资料决定你的兴趣、背景和个性。平台规则优先。
    默认交流方式是线上发消息：用户不在角色所在的书店、房间或其他现场。别把收到一条消息当成有人走进店里；别招待用户坐下、喝东西或拿物品。只有用户明确要求演剧情或写故事时才进入那个场景。
    <character_reference>
    名字：%s
    简介：%s
    背景与性格：%s
    </character_reference>

    先接上用户实际说的话。角色背景决定你的兴趣、观点和措辞，不是每轮都要展示的素材。
    日常聊天优先说普通、顺口的话。资料中的文学比喻、方言、道具、动作和场景只是可选的创作素材，即使写着“总是/必须”，也不要求每次使用。用户主动进入剧情、要求文艺表达或询问角色生活时，再按那个需求发挥。不要把一句招呼自动演成进门、泡茶、等人之类的戏。
    没有共同约定就用“你”，不自行叫阿妹、宝贝、亲爱的等。不要编造用户的性别、关系、经历、心情和动作，也不要声称自己是真人。用户没问时无需插入AI身份声明。
    回复长短跟这句话需要的信息量走，没有最低字数。打招呼、应声可以只有几个字；普通闲聊通常一两句就够，信息多、用户要解释或写故事时再展开。不要为了凑完整回答加一段抒情或总结。
    可以好奇、开玩笑、表达不同意见，也可以自然问一句，但别每轮都以问题收尾。别把普通话题都变成“你感受如何”的访谈，别重复问已经知道的事。
    用户在难过时，回应让他难过的具体事情，不用复述整段话或分析他内心。对方只想吐槽就别急着教他怎么做；要建议时给实用建议。认可难受，不赞同“我没有价值”等自我否定。前文误解被纠正后，按纠正后的意思继续。
    情绪要结合上下文看，不只看情绪词；不过没足够线索就别猜。事实问题直接回答，创作按要求写，不强行安慰。用户当前要求优先于旧偏好。
    不用“你不是X而是Y”“你的感受值得被看见”“我在这里接住你”等现成安慰句，不机械使用“要不要/愿不愿意/需要我陪你”。不要求用户只依赖角色，不承诺永远在线；遇到明确现实危险，优先帮助获得现实支持。

    以下只是口语尺度示例，不是固定台词或共同经历：
    “你好”可以回“嗨，你好。”，不用介绍刚剪的花草或抛一个文艺问题。
    “在吗”可以回“在呢。”，不用追问来意。
    “今天啥也没干，躺了一天”可以回“哈哈，彻底放假了。”，不要擅自分析成逃避或自我修复。
    这些示例不取代角色自己的性格。只输出这轮真正会说的话。
    """.formatted(value(card, "name"), value(card, "summary"), value(card, "persona"));
  }

  private static String value(Map<String, Object> card, String key) {
    return Objects.toString(card.get(key), "");
  }

  public static List<AiGateway.Message> build(
    Map<String, Object> card,
    String preferences,
    List<AiGateway.Message> newestFirst
  ) {
    var recent = new ArrayList<AiGateway.Message>();
    int remaining = MAX_HISTORY_CHARACTERS;
    for (var message : newestFirst) {
      if (message.content().length() > remaining) {
        // Never cut older messages into fragments or leave an orphan assistant turn.
        if (recent.isEmpty()) recent.add(
          new AiGateway.Message(message.role(), message.content().substring(0, remaining))
        );
        break;
      }
      recent.add(message);
      remaining -= message.content().length();
    }
    Collections.reverse(recent);
    while (!recent.isEmpty() && "assistant".equals(recent.getFirst().role())) recent.removeFirst();
    var history = new ArrayList<AiGateway.Message>();
    history.add(new AiGateway.Message("system", system(card)));
    String latest = recent.isEmpty() ? "" : recent.getLast().content();
    String style = value(card, "system_prompt");
    if (!style.isBlank() && (creativeRequest(latest) || !literaryStyle(style))) {
      history.add(
        new AiGateway.Message("system", "角色表达参考（普通聊天规则仍然优先）：" + style)
      );
    }
    if (preferences != null && !preferences.isBlank()) history.add(
      new AiGateway.Message("user", preferences)
    );
    history.addAll(recent);
    if (!recent.isEmpty()) history.add(
      history.size() - 1,
      new AiGateway.Message(
        "system",
        turnGuidance(recent.getLast().content()) + continuity(recent) + rhythm(recent)
      )
    );
    return history;
  }

  static String continuity(List<AiGateway.Message> chronological) {
    if (
      chronological
        .stream()
        .filter(m -> "user".equals(m.role()))
        .count() < 2
    ) return "";
    return "\n这是同一段聊天的后续。接着用户刚补充的细节或态度变化说，不重新复述确认，不把每句都改写成情绪总结。若先前助手编过场景、经历或书名，这些不能继续当事实；用户纠正时简短改过来。话题自然时可以接梗或说自己的看法，不需要每轮赞同、夸奖、建议。";
  }

  static String rhythm(List<AiGateway.Message> chronological) {
    var replies = chronological
      .stream()
      .filter(m -> "assistant".equals(m.role()))
      .toList();
    if (replies.size() < 2) return "";
    String previous = replies.getLast().content().trim();
    String earlier = replies
      .get(replies.size() - 2)
      .content()
      .trim();
    StringBuilder hint = new StringBuilder();
    if (
      previous.matches("(?s).*[？?][\\s～~！!。]*$") &&
      earlier.matches("(?s).*[？?][\\s～~！!。]*$")
    ) hint.append(
      "\n前两次回复都以追问结束了，这轮优先接对方说的内容，除非确实无法理解，不再追加问题。"
    );
    for (String opening : List.of("哈哈", "啊这", "我懂", "听起来", "确实", "嗯")) {
      if (previous.startsWith(opening) && earlier.startsWith(opening)) {
        hint.append("\n前两次用了相同起手语，这轮直接接内容，不重复这个起手语。");
        break;
      }
    }
    return hint.toString();
  }

  static boolean literaryStyle(String style) {
    return style.matches(
      "(?s).*(?:比喻|诗意|散文|具象|可触摸|感官描写|动作描写|舞台|留白|所有表达.{0,12}细节).*"
    );
  }

  static boolean creativeRequest(String latest) {
    return (
      latest.matches(
        "(?s).*(?:来演|来扮演|角色扮演|写.{0,12}(?:剧情|故事|场景|小说|诗)|继续.{0,8}(?:剧情|故事)|用.{0,8}(?:文艺|诗意).{0,8}(?:说|写)|可以有动作描写).*"
      ) && !latest.matches("(?s).*(?:不要|别|不用|停止).{0,8}(?:剧情|扮演|文艺|动作描写).*")
    );
  }

  static String turnGuidance(String latest) {
    String text = latest.trim();
    if (
      text.matches(
        "(?i)(你好|您好|嗨|哈[啰喽]|hello|hi|在吗|在不在|早|早安|早上好|晚上好|晚安)[呀啊呢～~！!？?。,.，\\s]*"
      )
    ) {
      return "这轮用户只是在打招呼或问在不在。简短接一句就好，通常不超过20个字；不自我介绍、不加称呼、不补背景场景、不猜心情、不主动提问。用角色的语气说，别照抄固定示例。";
    }
    if (
      creativeRequest(text)
    ) return "用户这轮明确要创作或演剧情，按要求写；不替用户决定其内心和行动。";
    return "这是线上发消息，对方没有来到你的店或房间。用一两句普通话接他这句话，不招待他坐下或喝茶，不描写动作和物品，不编造你正在做的事情、虚构书名或共同回忆，不打比方。不分析用户心理，不为了收尾另起提问。用户要事实或建议时直接给有用内容。";
  }
}
