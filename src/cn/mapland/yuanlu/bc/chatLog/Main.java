/**
 * auto: <br>
 * user: yuanlu<br>
 * date: 星期一 03 02 2020
 */
package cn.mapland.yuanlu.bc.chatLog;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import cn.mapland.yuanlu.bc.chatLog.Main.Filter.PlayerFilter;
import cn.mapland.yuanlu.bc.chatLog.Main.Filter.ServerFilter;
import cn.mapland.yuanlu.bc.chatLog.Main.Filter.StringFilter;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.HoverEvent.Action;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.event.ServerConnectedEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import net.md_5.bungee.event.EventHandler;

/**
 * yuanluBCchatLog
 * 
 * @author yuanlu
 *
 */
@SuppressWarnings("javadoc")
public class Main extends Plugin implements Listener {
	/**
	 * 过滤器
	 * 
	 * @author yuanlu
	 *
	 * @param <T> 数据类型
	 */
	public static abstract class Filter<T> {
		/**
		 * 玩家过滤器
		 * 
		 * @author yuanlu
		 *
		 */
		public static final class PlayerFilter extends Filter<ProxiedPlayer> {
			/**
			 * 从配置文件获取
			 * 
			 * @param config 配置文件
			 * @return 结果
			 */
			public static PlayerFilter get(Configuration config) {
				return new PlayerFilter(config.getStringList("name"));
			}

			/** 用户名 */
			List<String>		name;
			/** 用户名测试器 */
			Predicate<String>	namePredicate	= n -> name != null && name.contains(n);

			public PlayerFilter(List<String> name) {
				super();
				this.name = name;
			}

			public PlayerFilter(List<String> name, Predicate<String> namePredicate) {
				super();
				this.name			= name;
				this.namePredicate	= namePredicate;
			}

			@Override
			public boolean check(ProxiedPlayer player) {
				String name = player.getName();
				return namePredicate.test(name);
			}

		}

		/**
		 * 玩家过滤器
		 * 
		 * @author yuanlu
		 *
		 */
		public static final class ServerFilter extends Filter<ServerInfo> {
			/**
			 * 从配置文件获取
			 * 
			 * @param config 配置文件
			 * @return 结果
			 */
			public static ServerFilter get(Configuration config) {
				return new ServerFilter(config.getStringList("name"));
			}

			/** 用户名 */
			List<String>		name;
			/** 用户名测试器 */
			Predicate<String>	namePredicate	= n -> name != null && name.contains(n);

			public ServerFilter(List<String> name) {
				super();
				this.name = name;
			}

			public ServerFilter(List<String> name, Predicate<String> namePredicate) {
				super();
				this.name			= name;
				this.namePredicate	= namePredicate;
			}

			@Override
			public boolean check(ServerInfo serverInfo) {
				String name = serverInfo.getName();
				return namePredicate.test(name);
			}

		}

		/**
		 * 玩家过滤器
		 * 
		 * @author yuanlu
		 *
		 */
		public static final class StringFilter extends Filter<String> {
			/**
			 * 从配置文件获取
			 * 
			 * @param config 配置文件
			 * @return 结果
			 */
			public static StringFilter get(Configuration config) {
				return new StringFilter(config.getStringList("prefix"), config.getStringList("suffix"), config.getStringList("contain"));
			}

			/** 前缀 */
			List<String>		prefix;
			/** 前缀测试器 */
			Predicate<String>	prefixPredicate		= n -> prefix != null && forEach(prefix, p -> n.startsWith(p));
			/** 后缀 */
			List<String>		suffix;
			/** 后缀测试器 */
			Predicate<String>	suffixPredicate		= n -> suffix != null && forEach(suffix, p -> n.startsWith(p));
			/** 包含 */
			List<String>		contain;
			/** 包含测试器 */
			Predicate<String>	containPredicate	= n -> contain != null && forEach(contain, p -> n.contains(p));

			public StringFilter(List<String> prefix, List<String> suffix, List<String> contain) {
				super();
				this.prefix		= prefix;
				this.suffix		= suffix;
				this.contain	= contain;
			}

			public StringFilter(List<String> prefix, Predicate<String> prefixPredicate, List<String> suffix, Predicate<String> suffixPredicate,
					List<String> contain, Predicate<String> containPredicate) {
				super();
				this.prefix				= prefix;
				this.prefixPredicate	= prefixPredicate;
				this.suffix				= suffix;
				this.suffixPredicate	= suffixPredicate;
				this.contain			= contain;
				this.containPredicate	= containPredicate;
			}

			@Override
			public boolean check(String data) {
				return prefixPredicate.test(data) || //
						suffixPredicate.test(data) || //
						containPredicate.test(data);
			}
		}

		/**
		 * 检查数据是否<b>不</b>合格
		 * 
		 * @param data 数据
		 * @return 是否<b>不</b>合格
		 */
		public abstract boolean check(T data);

		/**
		 * 遍历元素测试是否符合条件<br>
		 * 如果有任一符合条件的元素则返回true<br>
		 * 工具
		 * 
		 * @param <E>        元素类型
		 * @param collection 元素集合
		 * @param predicate  测试器
		 * @return 是否有任一符合条件的元素
		 */
		protected <E> boolean forEach(Collection<E> collection, Predicate<E> predicate) {
			for (E e : collection) {
				if (predicate.test(e)) return true;
			}
			return false;
		}
	}

	/**
	 * 一条信息
	 * 
	 * @author yuanlu
	 *
	 */
	public final class Msg {
		/** 信息 */
		String							msg;
		/** 时间 */
		long							time;
		/** 玩家名称 */
		String							playerName;
		/** 服务器名称 */
		String							serverName;

		/** 文字组件缓存 */
		private transient BaseComponent	component;

		/** 文字组件缓存更新时重载次数 */
		private transient int			rc	= reload_count;

		public Msg(String msg, long time, String playerName, String serverName) {
			super();
			this.msg		= msg;
			this.time		= time;
			this.playerName	= playerName;
			this.serverName	= serverName;
		}

		/** @return 文字组件 */
		public BaseComponent getBaseComponent() {
			if (rc == reload_count && component != null) return component;
			rc = reload_count;
			Date date = new Date(time);
			return component = mb.build(serverName, playerName, msg, mb.date(date), date);
		}
	}

	/**
	 * 消息组件构建器
	 * 
	 * @author yuanlu
	 *
	 */
	public static final class MsgBuilder {
		/** 日期格式化 */
		SimpleDateFormat	sdf;
		/** 显示文字 */
		String				line;
		/** 悬浮文字 */
		String[]			hover;

		public MsgBuilder(String lineText, List<String> hovers, String dateFormat) {
			line	= Objects.requireNonNull(lineText, "lineText");
			hover	= hovers.toArray(new String[hovers.size()]);
			sdf		= new SimpleDateFormat(dateFormat);
		}

		/**
		 * 构建
		 * 
		 * @param data 数据
		 * @return 文字
		 */
		@SuppressWarnings("deprecation")
		public BaseComponent build(Object... data) {
			TextComponent	component	= new TextComponent(String.format(line, data));
			TextComponent[]	htc			= Tool.translate(hover, new TextComponent[hover.length], t -> new TextComponent(String.format(t, data)));
			HoverEvent		he			= new HoverEvent(Action.SHOW_TEXT, htc);
			component.setHoverEvent(he);
			return component;
		}

		/**
		 * 将日期转换为日期字符串
		 * 
		 * @param date 日期
		 * @return 字符串
		 * @see #sdf
		 */
		public String date(Date date) {
			return sdf.format(date);
		}
	}

	/** 已经展示过聊天记录的玩家 */
	private static final HashSet<String>	SHOWED_PLAYER		= new HashSet<>();

	/** 去除颜色码 */
	private static final Pattern			STRIP_COLOR_PATTERN	= Pattern.compile("(?i)" + String.valueOf('&') + "[0-9A-FK-OR]");

	/**
	 * 清除颜色
	 * 
	 * @param msg 语句
	 * @return 语句
	 */
	private static String clearColor(String msg) {
		return STRIP_COLOR_PATTERN.matcher(msg).replaceAll("");
	}

	/** 聊天记录前缀 */
	private String			prefix;

	/** 聊天记录后缀 */
	private String			suffix;
	/** 记录器 */
	private ObjLogger<Msg>	logger;
	/** 重载次数 */
	private int				reload_count;
	/** 文字组件构建器 */
	private MsgBuilder		mb;
	/** 过滤器 */
	private PlayerFilter	playerFilter;
	/** 过滤器 */
	private ServerFilter	serverFilter;
	/** 过滤器 */
	private StringFilter	messageFilter;

	/** 过滤器 */
	private PlayerFilter	nsplayerFilter;

	/** 过滤器 */
	private ServerFilter	nsserverFilter;

	/** 延时毫秒数 */
	private int				delay;

	/**
	 * 聊天事件
	 * 
	 * @param event 事件
	 * @deprecated BUNGEECORD
	 */
	@Deprecated
	@EventHandler
	public void event(ChatEvent event) {
		if (event.isCancelled()) return;
		if (!(event.getSender() instanceof ProxiedPlayer)) return;
		ProxiedPlayer	player	= (ProxiedPlayer) event.getSender();
		ServerInfo		server	= player.getServer().getInfo();
		String			msg		= clearColor(event.getMessage());
		if (playerFilter.check(player) || serverFilter.check(server) || messageFilter.check(msg)) return;
		long	time	= System.currentTimeMillis();
		String	pn		= player.getDisplayName();
		String	sn		= server.getName();
		logger.log(new Msg(msg, time, pn, sn));
	}

	/**
	 * 退出事件
	 * 
	 * @param event 事件
	 * @deprecated BUNGEECORD
	 */
	@Deprecated
	@EventHandler
	public void event(PlayerDisconnectEvent event) {
		synchronized (SHOWED_PLAYER) {
			SHOWED_PLAYER.remove(event.getPlayer().getName());
		}
	}

	/**
	 * 登录事件
	 * 
	 * @param event 事件
	 * @deprecated BUNGEECORD
	 */
	@Deprecated
	@EventHandler
	public void event(PostLoginEvent event) {
		ProxiedPlayer p = event.getPlayer();
		testShow(p, null, delay);
	}

	/**
	 * 登录事件
	 * 
	 * @param event 事件
	 * @deprecated BUNGEECORD
	 */
	@Deprecated
	@EventHandler
	public void event(ServerConnectedEvent event) {
		ProxiedPlayer	p	= event.getPlayer();
		Server			s	= event.getServer();
		testShow(p, s, delay);
	}

	/***/
	private synchronized void loadConfig() {
		Configuration	config		= loadFile("config.yml");
		int				log_num		= config.getInt("log-num");
		String			line_text	= config.getString("line-text");
		List<String>	hover_text	= config.getStringList("hover-text");
		String			date_format	= config.getString("date-format");
		prefix			= config.getString("prefix");
		suffix			= config.getString("suffix");
		playerFilter	= PlayerFilter.get(config.getSection("playerFilter"));
		serverFilter	= ServerFilter.get(config.getSection("serverFilter"));
		messageFilter	= StringFilter.get(config.getSection("msgFilter"));
		nsplayerFilter	= PlayerFilter.get(config.getSection("not-show-playerFilter"));
		nsserverFilter	= ServerFilter.get(config.getSection("not-show-serverFilter"));
		delay			= config.getInt("delay");
		if (logger == null) logger = new ObjLogger<>(log_num);
		else logger.setNewSize(log_num);

		mb = new MsgBuilder(line_text, hover_text, date_format);
		reload_count++;
	}

	/**
	 * 加载配置
	 * 
	 * @param fileName 配置文件名，例如{@code "config.yml"}
	 * @return 配置文件
	 * @author yuanlu
	 */
	public Configuration loadFile(String fileName) {
		if (!getDataFolder().exists()) getDataFolder().mkdir();
		try {
			File file = new File(getDataFolder(), fileName);
			if (!file.exists()) {
				try (InputStream in = getResourceAsStream(fileName)) {
					Files.copy(in, file.toPath());
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			return ConfigurationProvider.getProvider(YamlConfiguration.class).load(file);
		} catch (RuntimeException e) {
			throw e;
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void onEnable() {
		loadConfig();
		getProxy().getPluginManager().registerListener(this, this);// 注册监听器
		getLogger().info("加载完成");
	}

	/**
	 * 向玩家展示聊天记录
	 * 
	 * @param p 玩家
	 */
	@SuppressWarnings("deprecation")
	private void show(ProxiedPlayer p) {
		Object[] data = logger.read();
		p.sendMessage(String.format(prefix, data.length));
		for (Object msg : data) {
			p.sendMessage(((Msg) msg).getBaseComponent());
		}
		p.sendMessage(String.format(suffix, data.length));
	}

	/**
	 * 尝试向玩家展示聊天记录
	 * 
	 * @param p     p
	 * @param s     s
	 * @param delay 延时时长
	 */
	private void testShow(final ProxiedPlayer p, final Server s, final long delay) {

		if (delay > 0) {
			new Thread("delay(" + delay + "ms) show - " + p) {
				@Override
				public void run() {
					try {
						Thread.sleep(delay);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					testShow(p, s, 0);
				};
			}.start();
			return;
		}
		synchronized (SHOWED_PLAYER) {
			if (SHOWED_PLAYER.contains(p.getName())) return;
			if (nsplayerFilter.check(p)) return;
			Server t = s;
			if (t == null && (t = p.getServer()) == null) return;// 1.1.0出错的地方
			ServerInfo info = t.getInfo();
			if (nsserverFilter.check(info)) return;
			SHOWED_PLAYER.add(p.getName());
		}
		show(p);
	}
}
