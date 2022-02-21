package cn.mapland.yuanlu.bc.chatLog;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import lombok.NonNull;
import lombok.val;

/**
 * 日志记录器<br>
 * 用于循环记录日志
 * 
 * @author yuanlu
 * @param <E> 元素类型
 *
 */
public final class ObjLogger<E> {
	/** 日志 */
	private Object[]			logs;
	/** 指针 */
	private int					index	= 0;

	/** 锁 */
	private final ReadWriteLock	lock	= new ReentrantReadWriteLock();

	/**
	 * @param size 日志大小
	 */
	public ObjLogger(int size) {
		logs = new Object[size];
	}

	/**
	 * 记录日志
	 * 
	 * @param log 日志
	 */
	public void log(E log) {
		lock.writeLock().lock();
		try {
			logs[index++]	= log;
			index			%= logs.length;
		} finally {
			lock.writeLock().unlock();
		}
	}

	/** @return 日志最大数量 */
	public int getNum() {
		lock.readLock().lock();
		try {
			return logs.length;
		} finally {
			lock.readLock().unlock();
		}
	}

	/**
	 * 读取日志
	 * 
	 * @return 日志
	 */
	public Object[] read() {
		lock.readLock().lock();
		try {
			val index = this.index;
			if (logs[logs.length - 1] == null) {// 未记录满1次循环
				return Arrays.copyOf(logs, index);
			}
			Object[] back = new Object[logs.length];
			System.arraycopy(logs, index, back, 0, logs.length - index);
			System.arraycopy(logs, 0, back, logs.length - index, index);
			return back;
		} finally {
			lock.readLock().unlock();
		}
	}

	/**
	 * 读取日志
	 * 
	 * @param <T> 类型
	 * @param a   数组
	 * 
	 * @return 日志
	 */
	@SuppressWarnings("unchecked")
	public <T> T[] read(@NonNull T[] a) {
		lock.readLock().lock();
		try {
			val index = this.index;
			if (logs[logs.length - 1] == null) {// 未记录满1次循环
				if (a.length < index) return (T[]) Arrays.copyOf(logs, index, a.getClass());
				else return (T[]) Arrays.copyOf(logs, index, a.getClass());
			}
			if (a.length < logs.length) {
				Object back = Array.newInstance(a.getClass().getComponentType(), logs.length);
				System.arraycopy(logs, index, back, 0, logs.length - index);
				System.arraycopy(logs, 0, back, logs.length - index, index);
				return (T[]) back;
			} else {
				System.arraycopy(logs, index, a, 0, logs.length - index);
				System.arraycopy(logs, 0, a, logs.length - index, index);
				if (a.length > logs.length) a[logs.length] = null;
				return a;
			}
		} finally {
			lock.readLock().unlock();
		}
	}

	/**
	 * @param log_num 日志数量
	 */
	public void setNewSize(int log_num) {
		lock.writeLock().lock();
		try {
			if (logs.length == log_num) {
				return;
			} else if (logs.length > log_num) {
				Object[] old = read();
				logs = new Object[log_num];
				System.arraycopy(old, log_num - old.length, logs, 0, log_num);
				index = 0;
			} else {
				Object[] old = read();
				logs = new Object[log_num];
				System.arraycopy(old, 0, logs, 0, old.length);
				index = old.length;
			}
		} finally {
			lock.writeLock().unlock();
		}
	}
}