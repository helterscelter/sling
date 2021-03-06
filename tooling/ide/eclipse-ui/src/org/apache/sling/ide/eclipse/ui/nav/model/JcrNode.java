/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sling.ide.eclipse.ui.nav.model;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.sling.ide.eclipse.ui.internal.SharedImages;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.mapping.ResourceMapping;
import org.eclipse.core.resources.mapping.ResourceMappingContext;
import org.eclipse.core.resources.mapping.ResourceTraversal;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.IContributorResourceAdapter;
import org.eclipse.ui.model.WorkbenchLabelProvider;
import org.eclipse.ui.views.properties.IPropertySource;
import org.w3c.dom.Node;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/** WIP: model object for a jcr node shown in the content package view in project explorer **/
public class JcrNode implements IAdaptable {

    private final class JcrRootHandler extends DefaultHandler {
		boolean firstElement = true;
		boolean isVaultFile = false;

		public boolean isVaultFile() {
			return isVaultFile;
		}

		@Override
		public void startElement(String uri, String localName,
				String qName, Attributes attributes)
				throws SAXException {
			if (!firstElement) {
				return;
			}
			firstElement = false;
			if ("jcr:root".equals(qName)) {
				String ns = attributes.getValue("xmlns:jcr");
				if (ns!=null && ns.startsWith("http://www.jcp.org/jcr")) {
					// then this is a vault file with a jcr:root at the beginning
					isVaultFile = true;
				}
			}
		}
	}

	private final static WorkbenchLabelProvider workbenchLabelProvider = new WorkbenchLabelProvider();
	
	GenericJcrRootFile underlying;

	JcrNode parent;

	final Set<JcrNode> children = new HashSet<JcrNode>();

	Node domNode;

	private IResource resource;
	
	private boolean resourceChildrenAdded = false;
	
	final ReadOnlyProperties properties = new ReadOnlyProperties();
	
	JcrNode() {
		// for subclass use only
	}
	
	JcrNode(JcrNode parent, Node domNode) {
		if (parent == null) {
			throw new IllegalArgumentException("parent must not be null");
		}
		this.parent = parent;
		// domNode can be null
		this.domNode = domNode;
		this.underlying = parent.underlying;
		parent.addChild(this);
	}
	
	@Override
	public String toString() {
		return "JcrNode[dom:"+domNode+", file:"+resource+"]";
	}

	protected void addChild(JcrNode jcrNode) {
		children.add(jcrNode);
	}

	/** shown in the navigator (project explorer) as the label of this element **/
	public String getLabel() {
		if (domNode!=null && resource!=null) {
			return domNode.getNodeName();// + "[domnode+file]";
		} else if (domNode!=null && resource==null) {
			return domNode.getNodeName();// + "[domnode]";
		} else if (resource!=null) {
			return resource.getName();//+" [file]";
		} else {
			return "n/a";
		}
	}

	/** shown in the statusbar when this element is selected **/
	public String getDescription() {
		return getJcrPath();
	}

	public boolean hasChildren() {
		boolean members;
		try {
			members = resource!=null && resource instanceof IFolder && ((IFolder)resource).members().length>0;
		} catch (CoreException e) {
			members = false;
		}
		return children.size()>0 || members;
	}

	public Object[] getChildren() {
		try {
			if (resourceChildrenAdded) {
				return children.toArray();
			}
			Map<String,JcrNode> resultMap = new HashMap<String, JcrNode>();
			for (Iterator it = children.iterator(); it.hasNext();) {
				JcrNode node = (JcrNode) it.next();
				resultMap.put(node.getName(), node);
			}
			
			if (resource!=null && resource instanceof IFolder) {
				IFolder folder = (IFolder)resource;
				IResource[] members = folder.members();
				List<Object> membersList = new LinkedList<Object>(Arrays.asList(members));
				outerLoop: while(membersList.size()>0) {
					for (Iterator it = membersList.iterator(); it.hasNext();) {
						IResource iResource = (IResource) it.next();
						if (isVaultFile(iResource)) {
							GenericJcrRootFile gjrf = new GenericJcrRootFile(this, (IFile)iResource);
							it.remove();
							//gjrf.getChildren();
							gjrf.pickResources(membersList);
							
							// as this might have added some new children, go through the children again and
							// add them if they're not already added
							for (Iterator it3 = children.iterator(); it3
									.hasNext();) {
								JcrNode node = (JcrNode) it3.next();
								if (!resultMap.containsKey(node.getName())) {
									resultMap.put(node.getName(), node);
								}
							}
							
							continue outerLoop;
						}
					}
					for (Iterator it = membersList.iterator(); it.hasNext();) {
						IResource iResource = (IResource) it.next();
						JcrNode node = new JcrNode(this, (Node)null);
						node.setResource(iResource);
						node.init();
						// load the children - to make sure we get vault files loaded too
						node.getChildren();
						resultMap.put(node.getName(), node);
						it.remove();
					}
				}
			}
			resourceChildrenAdded = true;
			
			return resultMap.values().toArray();
		} catch (CoreException e) {
			return new Object[0];
		} catch (ParserConfigurationException e) {
			return new Object[0];
		} catch (SAXException e) {
			return new Object[0];
		} catch (IOException e) {
			return new Object[0];
		}
	}

	protected void init() {
		// placeholder for initializing any common properties
	}

	private boolean isVaultFile(IResource iResource)
			throws ParserConfigurationException, SAXException, IOException,
			CoreException {
		if (iResource.getName().endsWith(".xml")) {
			// then it could potentially be a vlt file. 
			// let's check if it contains a jcr:root element with the appropriate namespace
			
			IFile file = (IFile)iResource;
			
		    SAXParserFactory factory = SAXParserFactory.newInstance();
		    SAXParser saxParser = factory.newSAXParser();

		    JcrRootHandler h = new JcrRootHandler();
			saxParser.parse(new InputSource(file.getContents()), h);

			return h.isVaultFile();
		}
		return false;
	}

	public void setResource(IResource resource) {
		if (this.resource!=null) {
			throw new IllegalStateException("can only set resource once");
		}
		this.resource = resource;
	}

	public Image getImage() {
		if (resource!=null) {
			if (resource instanceof IFolder) {
				Image folderImage = workbenchLabelProvider.getImage(resource);
				if (domNode==null && false) {
					// then make it greyscale
					folderImage = new
							Image(folderImage.getDevice(), folderImage,SWT.IMAGE_GRAY);
				}
				return folderImage;
			} else
				try {
					if (!isVaultFile(resource)){
						return workbenchLabelProvider.getImage(resource);
					}
				} catch (ParserConfigurationException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (SAXException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (CoreException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		}
		//if (domNode!=null && children!=null && children.size()>0) {
		//	return workbenchLabelProvider.getImage(domNode);
		//}
		
		return SharedImages.SLING_ICON.createImage();
	}

	public String getName() {
		if (domNode!=null) {
			return domNode.getNodeName();
		} else if (resource!=null) {
			return resource.getName();
		} else {
			return "";
		}
	}
		
	String getJcrPath() {
		String prefix;
		if (parent==null) {
			prefix = "";
		} else {
			prefix = parent.getJcrPath();
			if (!prefix.endsWith("/")) {
				prefix = prefix + "/";
			}
		}
		return prefix + getName();
	}

	@Override
	public Object getAdapter(Class adapter) {
		final Object result = doGetAdapter(adapter);
		if (result==null) {
		//	System.out.println("adapter looked for: "+adapter+", result: "+result);
		}
		return result;
	}
	
	private Object doGetAdapter(Class adapter) {
		if (adapter==IPropertySource.class) {
			return properties;
		} else if (adapter == IFile.class) {
			if (resource instanceof IFile) {
				return (IFile)resource;
			} else {
				return null;
			}
		} else if (adapter == IContributorResourceAdapter.class) {
			if (resource==null) {
				return null;
			}
			return new IContributorResourceAdapter() {
				
				@Override
				public IResource getAdaptedResource(IAdaptable adaptable) {
					if (!(adaptable instanceof JcrNode)) {
						return null;
					}
					JcrNode node = (JcrNode)adaptable;
					if (node.resource!=null) {
						return node.resource;
					}
					return null;
				}
			};
		} else if (adapter == IResource.class) {
			return resource;		
		} else if (adapter == ResourceMapping.class) { 
			boolean t = true;
			if (!t) {
				return null;
			}
			if (resource==null) {
				return null;
			}
			return new ResourceMapping() {
				
				@Override
				public ResourceTraversal[] getTraversals(ResourceMappingContext context,
						IProgressMonitor monitor) throws CoreException {
					return new ResourceTraversal[] { new ResourceTraversal(new IResource[] { resource }, IResource.DEPTH_INFINITE, IResource.NONE) };
				}
				
				@Override
				public IProject[] getProjects() {
					if (resource!=null) {
						return new IProject[] {resource.getProject()};
					}
					return null;
				}
				
				@Override
				public String getModelProviderId() {
					return "org.apache.sling.ide.eclipse.ui.nav.model.JcrNode.ResourceMapping";
				}
				
				@Override
				public Object getModelObject() {
					return resource;
				}
			};
		}
		return null;
	}

}
