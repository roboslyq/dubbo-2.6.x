/**
 * 
 */
package com.roboslyq.dubbo.learn;

import com.alibaba.dubbo.common.extension.ExtensionLoader;
import com.alibaba.dubbo.rpc.Protocol;

/**
 * @author robos
 *
 */
public class ExtensionLearn {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		Protocol p = ExtensionLoader.getExtensionLoader(Protocol.class).getAdaptiveExtension();
		Protocol p1 = ExtensionLoader.getExtensionLoader(Protocol.class).getDefaultExtension();
		Protocol p2 = ExtensionLoader.getExtensionLoader(Protocol.class).getLoadedExtension("xxxxx");
		
	}

}
