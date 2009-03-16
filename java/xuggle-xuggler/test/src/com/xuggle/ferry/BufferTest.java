package com.xuggle.ferry;

import static junit.framework.Assert.*;

import java.nio.ByteBuffer;

import com.xuggle.ferry.IBuffer;
import com.xuggle.test_utils.NameAwareTestClassRunner;

import org.junit.*;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(NameAwareTestClassRunner.class)
public class BufferTest
{
  private final Logger log = LoggerFactory.getLogger(this.getClass());

  private String mTestName = null;
  @Before
  public void setUp()
  {
    mTestName = NameAwareTestClassRunner.getTestMethodName();
    log.debug("-----START----- {}", mTestName);
  }
  @After
  public void tearDown()
  {
    log.debug("----- END ----- {}", mTestName);
  }

  /**
   * Test we can create an ibuffer of the right size.
   */
  @Test
  public void testCreation()
  {
    IBuffer buf = IBuffer.make(null, 1024);
    assertNotNull(buf);
    assertTrue(buf.getBufferSize()>=1024);
  }

  /**
   * Test that we can create a IBuffer from a Java byte[] array,
   * and that we can copy the same data out of an IBuffer (via copy).
   */
  @Test
  public void testCreateFromBytes()
  {
    byte buffer[] = { 0, 1, 2, 3, 4, 5, 6, 7, 8 ,9 };
    IBuffer buf = IBuffer.make(null, buffer, 0, buffer.length);
    assertNotNull(buf);
    assertEquals(buf.getBufferSize(), buffer.length);
    byte outBuffer[] = buf.getByteArray(0, buffer.length);
    assertNotNull(outBuffer);
    assertEquals(outBuffer.length, buffer.length);
    assertNotSame(buf, outBuffer);
    for(int i =0 ; i < buffer.length; i++)
    {
      assertEquals(buffer[i], outBuffer[i]);
    }
  }
  
  /**
   * Test we can create an IBuffer, then modify the direct
   * bytes in native code.
   */
  @Test
  public void testCanDirectlyModifyNativeBytes()
  {
    byte buffer[] = { 0, 1, 2, 3, 4, 5, 6, 7, 8 ,9 };
    IBuffer buf = IBuffer.make(null, buffer, 0, buffer.length);
    assertNotNull(buf);
    assertEquals(buf.getBufferSize(), buffer.length);
    
    // this give us the native bytes
    java.nio.ByteBuffer nativeBytes = buf.getByteBuffer(0, buffer.length);
    assertNotNull(nativeBytes);
    for(int i = 0; i < buffer.length; i++)
    {
      nativeBytes.put(i, (byte)(9-buffer[i])); // reverse the order of the integers
    }
    // we can release it.  no "update" method should be required.  It should
    // have modified the underlying C++ bytes.
    nativeBytes = null;
    
    // now, get a copy of the bytes in the IBuffer and make sure
    // the order of bytes was reversed.
    byte outBuffer[] = buf.getByteArray(0, buffer.length);
    assertNotNull(outBuffer);
    assertEquals(outBuffer.length, buffer.length);
    assertNotSame(buf, outBuffer);
    for(int i =0 ; i < buffer.length; i++)
    {
      assertEquals(9-buffer[i], outBuffer[i]);
    }
  }
  
  @Test(expected=IllegalArgumentException.class)
  public void testCreationFailsWithoutDirectByteBuffer()
  {
    ByteBuffer directByteBuffer = ByteBuffer.allocate(10);
    IBuffer.make(null, directByteBuffer, 0, 10);
  }
  
  /**
   * Tests if we can create an IBuffer from a Java direct ByteBuffer.
   */
  @Test
  public void testCreationFromJavaDirectByteBuffer()
  {
    int numBytes = 10;
    ByteBuffer directByteBuffer = ByteBuffer.allocateDirect(numBytes);
    //assertTrue(directByteBuffer.isDirect());
    for(int i = 0; i < numBytes; i++)
    {
      directByteBuffer.put(i, (byte)i);
    }
    
    // 
    IBuffer ibuf = IBuffer.make(null, directByteBuffer, 0, numBytes);
    assertNotNull(ibuf);
    
    ByteBuffer outputDirectByteBuffer = ibuf.getByteBuffer(0, numBytes);
    assertNotNull(numBytes);
    assertEquals(numBytes, outputDirectByteBuffer.capacity());
    for(int i = 0; i < numBytes; i++)
    {
      assertEquals(i, outputDirectByteBuffer.get(i));
    }
  }
  
  
  /**
   * Tests if we can create an IBuffer from a Java direct ByteBuffer, and
   * then modify the data from the original Java byte buffer
   */
  @Test
  public void testCreationFromJavaDirectByteBufferAndModify()
  {
    int numBytes = 10;
    ByteBuffer directByteBuffer = ByteBuffer.allocateDirect(numBytes);
    //assertTrue(directByteBuffer.isDirect());
    for(int i = 0; i < numBytes; i++)
    {
      directByteBuffer.put(i, (byte)i);
    }
    
    // 
    IBuffer ibuf = IBuffer.make(null, directByteBuffer, 0, numBytes);
    assertNotNull(ibuf);
    
    ByteBuffer outputDirectByteBuffer = ibuf.getByteBuffer(0, numBytes);
    assertNotNull(numBytes);
    assertEquals(numBytes, outputDirectByteBuffer.capacity());
    for(int i = 0; i < numBytes; i++)
    {
      assertEquals(i, outputDirectByteBuffer.get(i));
    }
    
    // Now modify the original data
    for(int i = 0; i < numBytes; i++)
    {
      directByteBuffer.put(i, (byte)(numBytes-i-1));
    }
    
    // And make sure the copy we got out points to the same data
    for(int i = 0; i < numBytes; i++)
    {
      assertEquals(numBytes-i-1, outputDirectByteBuffer.get(i));
    }
    
    
  }  
  /**
   * This method allocates one large IBuffer and then repeatedly
   * copies out the bytes into a Java byte[] array.
   * 
   * If the system is not leaking, the garbage collector will ensure
   * we don't run out of heap space.  If we're leaking, bad things
   * will occur.
   */
  @Test
  public void testNoLeakingMemoryOnCopy()
  {
    IBuffer buf = IBuffer.make(null, 1024*1024); // 1 MB
    assertNotNull(buf);
    for(int i = 0; i < 1000; i++)
    {
      byte[] outBytes = buf.getByteArray(0, buf.getBufferSize());
      // and we do nothing with the outBytes
      assertEquals(outBytes.length, buf.getBufferSize());
      outBytes = null;
    }
  }
  
  /**
   * This method allocates one large IBuffer and then repeatedly
   * creates a java.nio.ByteBuffer to access them
   * 
   * If the system is not leaking, the garbage collector will ensure
   * we don't run out of heap space.  If we're leaking, bad things
   * will occur.
   */
  @Test
  public void testNoLeakingMemoryOnDirectAccess()
  {
    IBuffer buf = IBuffer.make(null, 1024*1024); // 1 MB
    assertNotNull(buf);
    for(int i = 0; i < 100000; i++)
    {
      java.nio.ByteBuffer nativeBytes = buf.getByteBuffer(0, buf.getBufferSize());
      // and we do nothing with the outBytes
      assertEquals(nativeBytes.limit(), buf.getBufferSize());
      nativeBytes = null;
    }
  }
  
  /**
   * This tests tries to make sure the IBuffer correctly releases any
   * references it might hold to the underlying java.nio.ByteBuffer
   * it wraps.
   * 
   * It does this by creating a lot of large IBuffer objects.
   */
  @Test
  public void testNoLeaksWhenBufferMadeFromDirectJavaByteBuffer()
  {
    for(int i = 0; i < 100; i++)
    {
      ByteBuffer nativeBytes = ByteBuffer.allocateDirect(10*1024*1024);
      IBuffer buf = IBuffer.make(null, nativeBytes, 0, nativeBytes.capacity());
      assertNotNull(buf);
      buf.delete();
      buf = null;
      
      // We need to do three GC's because it appears Java NIO ByteBuffers sometimes rely
      // on finalizers to free memory.  The first GC will mark something for finalization
      // The second will finalize, and the third will reclaim.
      System.gc();
      System.gc();
      System.gc();
      Thread.yield();
    }
  }

  /**
   * This is a crazy test to make sure that a direct byte buffer will
   * decrement ref counts if freed by java Garbage Collector and
   * our garbage collector
   */
  @Test(timeout=5000)
  public void testDirectByteBufferIncrementsAndDecrementsRefCounts()
  {
    IBuffer buf = IBuffer.make(null, 1024*1024); // 1 MB
    assertNotNull(buf);
    
    assertEquals(1, buf.getCurrentRefCount());

    java.nio.ByteBuffer jbuf = buf.getByteBuffer(0, buf.getBufferSize());
    assertNotNull(buf);
    
    assertEquals(2, buf.getCurrentRefCount());

    jbuf.put(0, (byte)0xFF);
    
    // now release the reference
    jbuf = null;
    
    while(buf.getCurrentRefCount() >= 2)
    {
      System.gc();
      JNIWeakReference.getMgr().gc();
    }
  }

  /**
   * This is a crazy test to make sure that a direct byte buffer will
   * still be accessible even if the IBuffer it came from goes out of
   * scope and is collected.
   */
  @Test(timeout=5000)
  public void testDirectByteBufferCanBeAccessedAfterIBufferDisappears()
  {
    IBuffer buf = IBuffer.make(null, 1024*1024); // 1 MB
    assertNotNull(buf);
    
    assertEquals(1, buf.getCurrentRefCount());

    java.nio.ByteBuffer jbuf = buf.getByteBuffer(0, buf.getBufferSize());
    assertNotNull(buf);
    
    // now release the reference
    buf.delete();
    buf = null;

    // in versions prior to 1.22, this would have caused a hard
    // crash, but with 1.22 the getByteBuffer should have incremented
    // the native ref count until this java ByteBuffer gets collected
    // and we do a JNIMemoryManager gc.
    jbuf.put(0, (byte)0xFF);
  }

}