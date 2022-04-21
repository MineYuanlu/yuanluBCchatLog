/**
 * 
 */
package cn.mapland.yuanlu.bc.chatLog;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
import org.bstats.velocity.Metrics;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import com.google.common.base.Charsets;
import com.google.inject.Inject;
import com.moandjiezana.toml.Toml;
import com.moandjiezana.toml.TomlWriter;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.ServerInfo;

import cn.mapland.yuanlu.bc.chatLog.Filter.PlayerFilter;
import cn.mapland.yuanlu.bc.chatLog.Filter.ServerFilter;
import cn.mapland.yuanlu.bc.chatLog.Filter.StringFilter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.val;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.HoverEvent;

/**
 * 聊天记录
 * 
 * @author yuanlu
 *
 */
@Plugin(//
		id = "yuanluchatlog", //
		name = "元路聊天记录插件", //
		authors = "yuanlu", //
		url = "https://git.yuanlu.bid/yuanlu/yuanluBCchatLog/src/branch/Velocity", //
		description = "记录玩家的聊天记录"//
)
public class Main {
	/** main */
	private static Main		main;
	/** server */
	final ProxyServer		server;
	/** logger */
	final Logger			logger;
	/** 插件目录 */
	final Path				dataDirectory;
	/** bstats */
	final Metrics.Factory	metricsFactory;
	/** class loader */
	final ClassLoader		classLoader;

	/** 配置文件 */
	Toml					config;

	@SuppressWarnings("javadoc")
	@Inject
	public Main(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory, Metrics.Factory metricsFactory) {
		main				= this;
		this.server			= server;
		this.logger			= logger;
		this.dataDirectory	= dataDirectory;
		this.metricsFactory	= metricsFactory;
		this.classLoader	= getClass().getClassLoader();
	}

	/** @return the main */
	public static Main getMain() {
		return main;
	}

	/** 聊天记录前缀 */
	private String				prefix;
	/** 聊天记录后缀 */
	private String				suffix;
	/** 过滤器 */
	private Filter<Player>		playerFilter;
	/** 过滤器 */
	private Filter<ServerInfo>	serverFilter;
	/** 过滤器 */
	private Filter<String>		messageFilter;
	/** 过滤器 */
	private Filter<Player>		nsplayerFilter;
	/** 过滤器 */
	private Filter<ServerInfo>	nsserverFilter;

	/** 文字组件构建器 */
	private MsgBuilder			mb;
	/** 记录器 */
	private ObjLogger<Msg>		chatLogger;
	/** 重载次数 */
	private int					reload_count;
	/** 延时毫秒数 */
	private long				delay;

	/**
	 * 一条信息
	 * 
	 * @author yuanlu
	 *
	 */
	@RequiredArgsConstructor
	public final class Msg {
		/** 信息 */
		private final String		msg;
		/** 时间 */
		private final long			time;
		/** 玩家名称 */
		private final String		playerName;
		/** 服务器名称 */
		private final String		serverName;

		/** 文字组件缓存 */
		private transient Component	component;

		/** 文字组件缓存更新时重载次数 */
		private transient int		rc	= reload_count;

		/** @return 文字组件 */
		public synchronized Component getBaseComponent() {

			int			rc, reload_count;
			Component	component;

			rc				= this.rc;
			reload_count	= Main.this.reload_count;
			component		= this.component;
			if (rc == reload_count && component != null) return component;

			this.rc = Main.this.reload_count;
			Date date = new Date(time);
			return this.component = mb.build(serverName, playerName, msg, mb.date(date), date);

		}
	}

	/**
	 * 退出事件
	 * 
	 * @param event 事件
	 * @deprecated EVENT
	 */
	@Deprecated
	@Subscribe
	public void onDisconnect(@NonNull DisconnectEvent event) {
		SHOWED_PLAYER.remove(event.getPlayer().getUsername());
	}

	/**
	 * 登录事件
	 * 
	 * @param event 事件
	 * @deprecated EVENT
	 */
	@Deprecated
	@Subscribe
	public void event(@NonNull PostLoginEvent event) {
		Player p = event.getPlayer();
		testShow(p, null, delay);
	}

	/**
	 * 登录事件
	 * 
	 * @param event 事件
	 * @deprecated EVENT
	 */
	@Deprecated
	@Subscribe
	public void event(@NonNull ServerConnectedEvent event) {
		Player p = event.getPlayer();
		testShow(p, event.getServer().getServerInfo(), delay);
	}

	/**
	 * 聊天
	 * 
	 * @param event PlayerChatEvent
	 * @deprecated EVENT
	 */
	@Deprecated
	@Subscribe(order = PostOrder.LAST)
	public void onChat(@NonNull PlayerChatEvent event) {
		if (!event.getResult().isAllowed()) return;
		Player				player	= event.getPlayer();
		ServerConnection	server	= player.getCurrentServer().orElse(null);
		ServerInfo			info	= server.getServerInfo();
		String				msg		= clearColor(event.getMessage());
		if (playerFilter.check(player) || serverFilter.check(info) || messageFilter.check(msg)) return;
		long	time	= System.currentTimeMillis();
		String	pn		= player.getUsername();
		String	sn		= info.getName();
		chatLogger.log(new Msg(msg, time, pn, sn));
		MSG_COUNT.getAndIncrement();
	}

	/** 已经展示过聊天记录的玩家 */
	private static final Set<String>	SHOWED_PLAYER		= new ConcurrentSkipListSet<>();

	/** 去除颜色码 */
	private static final Pattern		STRIP_COLOR_PATTERN	= Pattern.compile("(?i)" + String.valueOf('&') + "[0-9A-FK-OR]");
	/** bstats: 聊天统计 */
	private static final AtomicInteger	MSG_COUNT			= new AtomicInteger();

	/**
	 * 清除颜色
	 * 
	 * @param msg 语句
	 * @return 语句
	 */
	private static String clearColor(@NonNull String msg) {
		return STRIP_COLOR_PATTERN.matcher(msg).replaceAll("");
	}

	/**
	 * 初始化
	 * 
	 * @param event ProxyInitializeEvent
	 * @deprecated EVENT
	 */
	@Deprecated
	@Subscribe(order = PostOrder.LAST)
	public void onProxyInitialization(ProxyInitializeEvent event) {
		bstats();
		Toml	toml		= config = loadConf("config");
		int		log_num		= Tool.get("log-num", 50L, toml::getLong).intValue();
		String	line_text	= Tool.getConf("line-text", "§7> [%1$s]%2$s§7: %3$s", String.class);
		String	hover_text	= Tool.getConf("line-text", null, String.class);
		String	date_format	= Tool.get("date-format", "yyyy-MM-dd HH:mm:ss", toml::getString);
		prefix			= Tool.getConf("prefix", null, String.class);
		suffix			= Tool.getConf("suffix", null, String.class);
		playerFilter	= PlayerFilter.get(Tool.get("playerFilter", null, toml::getTable));
		serverFilter	= ServerFilter.get(Tool.get("serverFilter", null, toml::getTable));
		messageFilter	= StringFilter.get(Tool.get("msgFilter", null, toml::getTable));
		nsplayerFilter	= PlayerFilter.get(Tool.get("not-show-playerFilter", null, toml::getTable));
		nsserverFilter	= ServerFilter.get(Tool.get("not-show-serverFilter", null, toml::getTable));
		delay			= Tool.get("delay", 50L, toml::getLong);

		if (chatLogger == null) chatLogger = new ObjLogger<>(log_num);
		else chatLogger.setNewSize(log_num);

		mb = new MsgBuilder(line_text, hover_text, date_format);
	}

	/** bstats */
	private void bstats() {

		Metrics metrics = metricsFactory.make(this, 14424);

		metrics.addCustomChart(new SingleLineChart("logs_max", () -> chatLogger == null ? 0 : chatLogger.getNum()));
		metrics.addCustomChart(new SingleLineChart("msgs", () -> MSG_COUNT.getAndSet(0)));
		metrics.addCustomChart(new SimplePie("pls_count", () -> {
			val count = server.getPluginManager().getPlugins().stream()//
					.filter(p -> p.getDescription().getAuthors().contains("yuanlu"))//
					.count();
			return Long.toString(count);
		}));
	}

	/**
	 * 消息组件构建器
	 * 
	 * @author yuanlu
	 *
	 */
	public static final class MsgBuilder {
		/** 日期格式化 */
		private final SimpleDateFormat	sdf;
		/** 显示文字 */
		private final String			line;
		/** 悬浮文字 */
		private final String			hover;

		@SuppressWarnings("javadoc")
		public MsgBuilder(@NonNull String lineText, @NonNull String hoverText, @NonNull String dateFormat) {
			line	= lineText;
			hover	= hoverText;
			sdf		= new SimpleDateFormat(dateFormat);
		}

		/**
		 * 构建
		 * 
		 * @param data 数据
		 * @return 文字
		 */
		public Component build(Object... data) {

			TextComponent component = Component.text(String.format(line, data));
			component.hoverEvent(HoverEvent.showText(Component.text(String.format(hover, data))));

			return component;
		}

		/**
		 * 将日期转换为日期字符串
		 * 
		 * @param date 日期
		 * @return 字符串
		 * @see #sdf
		 */
		public String date(@NonNull Date date) {
			synchronized (sdf) {
				return sdf.format(date);
			}
		}
	}

	/**
	 * 加载配置文件<br>
	 * <ol>
	 * <li>优先使用.toml</li>
	 * <li>其次转换.yaml</li>
	 * <li>最后使用内置.toml</li>
	 * </ol>
	 * 
	 * @param name 配置文件名称
	 * @return 配置文件
	 * @throws IllegalStateException 无法解析
	 */
	private Toml loadConf(@NonNull String name) throws IllegalStateException {

		val tomlPath = dataDirectory.resolve(name + ".toml");
		try {
			Files.createDirectories(tomlPath.getParent());
		} catch (Throwable e1) {
			throw new IllegalStateException("Can not create dir: " + tomlPath.getParent(), e1);
		}

		// find .toml
		try {
			if (Files.exists(tomlPath)) {
				try (val in = Files.newBufferedReader(tomlPath, Charsets.UTF_8)) {
					StringJoiner sj = new StringJoiner("\n");
					in.lines().forEach(sj::add);
					return new Toml().read(sj.toString());
				}
			}
		} catch (IllegalStateException e) {
			throw e;
		} catch (Throwable e) {
			throw new IllegalStateException("Can not read: " + tomlPath, e);
		}

		// trans .yaml
		val yamlPath = dataDirectory.resolve(name + ".yml");
		try {
			if (Files.exists(yamlPath)) {
				Object yamlObj;
				try (val in = Files.newBufferedReader(yamlPath, Charsets.UTF_8)) {
					yamlObj = new Yaml().load(in);
				}
				val tomlStr = new TomlWriter().write(yamlObj);
				try (val out = Files.newOutputStream(tomlPath)) {
					out.write(tomlStr.getBytes(Charsets.UTF_8));
				}
				return new Toml().read(tomlStr);
			}
		} catch (IllegalStateException e) {
			throw e;
		} catch (Throwable e) {
			throw new IllegalStateException("Can not read: " + yamlPath, e);
		}

		// load inner .toml
		try {
			@lombok.Cleanup //
			val in = classLoader.getResourceAsStream(name + ".toml");
			if (in != null) {
				val bin = Tool.toByte(in);
				bin.mark(0);
				Files.copy(bin, tomlPath, StandardCopyOption.REPLACE_EXISTING);
				bin.reset();
				val toml = new Toml().read(bin);

				return toml;
			}
		} catch (IllegalStateException e) {
			throw e;
		} catch (Throwable e) {
			throw new IllegalStateException("Can not read: " + yamlPath, e);
		}

		return null;
	}

	/**
	 * 尝试向玩家展示聊天记录
	 * 
	 * @param p     p
	 * @param s     s
	 * @param delay 延时时长
	 */
	private void testShow(@NonNull final Player p, final ServerInfo s, final long delay) {

		if (p == null || SHOWED_PLAYER.contains(p.getUsername())) return;

		if (delay > 0) {
			server.getScheduler()//
					.buildTask(this, () -> testShow(p, s, 0))//
					.delay(delay, TimeUnit.MILLISECONDS)//
					.schedule();
			return;
		}

		if (nsplayerFilter.check(p)) {
			SHOWED_PLAYER.add(p.getUsername());
			return;
		}

		ServerInfo t = s;
		if (t == null) {
			ServerConnection server = p.getCurrentServer().orElse(null);
			if (server == null) return;
			else t = server.getServerInfo();
		}

		if (nsserverFilter.check(t)) {
			SHOWED_PLAYER.add(p.getUsername());
			return;
		}

		if (!SHOWED_PLAYER.add(p.getUsername())) return;

		show(p);
	}

	/**
	 * 向玩家展示聊天记录
	 * 
	 * @param p 玩家
	 */
	private void show(@NonNull Player p) {
		Object[] data = chatLogger.read();
		p.sendMessage(Component.text(String.format(prefix, data.length)));
		for (Object msg : data) {
			p.sendMessage(((Msg) msg).getBaseComponent());
		}
		p.sendMessage(Component.text(String.format(suffix, data.length)));
	}
}
