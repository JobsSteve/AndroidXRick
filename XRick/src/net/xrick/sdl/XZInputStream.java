/*
 Simple DirectMedia Layer
 Java source code (C) 2009-2014 Sergii Pylypenko

 This software is provided 'as-is', without any express or implied
 warranty.  In no event will the authors be held liable for any damages
 arising from the use of this software.

 Permission is granted to anyone to use this software for any purpose,
 including commercial applications, and to alter it and redistribute it
 freely, subject to the following restrictions:

 1. The origin of this software must not be misrepresented; you must not
 claim that you wrote the original software. If you use this software
 in a product, an acknowledgment in the product documentation would be
 appreciated but is not required. 
 2. Altered source versions must be plainly marked as such, and must not be
 misrepresented as being the original software.
 3. This notice may not be removed or altered from any source distribution.
 */

package net.xrick.sdl;

import java.io.InputStream;
import java.io.IOException;
import android.util.Log;

/**
 * Decompresses a .xz file in streamed mode (no seeking). This is a copy of code
 * from http://git.tukaani.org/xz-java.git but using liblzma and JNI instead of
 * Java, because Java heap is very limited, and we're hitting memory limit on
 * emulator.
 */
public class XZInputStream extends InputStream {
	private long nativeData = 0;
	private InputStream in = null;
	private final byte[] inBuf = new byte[8192];
	private int inOffset = 0;
	private int inAvailable = 0;
	private boolean outBufEof = false;
	private int offsets[] = new int[2];

	private final byte[] tempBuf = new byte[1];

	public XZInputStream(InputStream in) throws IOException {
		this.in = in;
		if (in == null) {
			throw new NullPointerException("InputStream == null");
		}
		nativeData = nativeInit();
		if (nativeData == 0) {
			throw new OutOfMemoryError("Cannot initialize JNI liblzma object");
		}
	}

	@Override
	public int available() throws IOException {
		return 0; // Don't care
	}

	@Override
	public void close() throws IOException {
		synchronized (this) {
			if (nativeData != 0)
				nativeClose(nativeData);
			nativeData = 0;
			if (in != null) {
				try {
					in.close();
				} finally {
					in = null;
				}
			}
		}
	}

	@Override
	protected void finalize() throws IOException {
		try {
			close();
		} finally {
			try {
				super.finalize();
			} catch (Throwable t) {
				throw new AssertionError(t);
			}
		}
	}

	@Override
	public int read() throws IOException {
		return read(tempBuf, 0, 1) == -1 ? -1 : (tempBuf[0] & 0xFF);
	}

	@Override
	public int read(byte[] outBuf, int outOffset, int outCount) throws IOException {
		Log.i("SDL", "XZInputStream.read: outOffset " + outOffset + " outCount " + outCount + " outBufEof " + outBufEof + " inOffset " + inOffset + " inAvailable " + inAvailable);
		if (outBufEof)
			return -1;
		if (outCount <= 0)
			return 0;

		int oldOutOffset = outOffset;

		if (inOffset >= inAvailable && inAvailable != -1) {
			inAvailable = in.read(inBuf, 0, inBuf.length);
			inOffset = 0;
			Log.i("SDL", "XZInputStream.read: in.read: inOffset " + inOffset + " inAvailable " + inAvailable);
		}

		offsets[0] = inOffset;
		offsets[1] = outOffset;
		int ret = nativeRead(nativeData, inBuf, inAvailable, outBuf, outCount, offsets);
		inOffset = offsets[0];
		outOffset = offsets[1];
		Log.i("SDL", "XZInputStream.read: nativeRead: outOffset " + outOffset + " outCount " + outCount + " outBufEof " + outBufEof + " inOffset " + inOffset + " inAvailable " + inAvailable + " ret " + ret);

		if (ret != 0) {
			if (ret == 1) {
				if (inOffset < inAvailable)
					throw new IOException("Garbage at the end of LZMA stream");
				if (inAvailable != -1)
					inAvailable = in.read(inBuf, 0, inBuf.length);
				if (inAvailable != -1)
					throw new IOException("Garbage at the end of LZMA stream");
				outBufEof = true;
			} else {
				throw new IOException("LZMA error " + ret);
			}
		}

		Log.i("SDL", "XZInputStream.read: returning " + (outOffset - oldOutOffset));
		return outOffset - oldOutOffset;
	}

	private native long nativeInit();

	private native void nativeClose(long nativeData);

	private native int nativeRead(long nativeData, byte[] inBuf, int inAvailable, byte[] outBuf, int outCount, int[] offsets);
}