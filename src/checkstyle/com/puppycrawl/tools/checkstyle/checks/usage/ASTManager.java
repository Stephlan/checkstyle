////////////////////////////////////////////////////////////////////////////////
//checkstyle: Checks Java source code for adherence to a set of rules.
//Copyright (C) 2001-2003  Oliver Burn
//
//This library is free software; you can redistribute it and/or
//modify it under the terms of the GNU Lesser General Public
//License as published by the Free Software Foundation; either
//version 2.1 of the License, or (at your option) any later version.
//
//This library is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
//Lesser General Public License for more details.
//
//You should have received a copy of the GNU Lesser General Public
//License along with this library; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
////////////////////////////////////////////////////////////////////////////////
package com.puppycrawl.tools.checkstyle.checks.usage;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import antlr.collections.AST;

import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.checks.usage.transmogrify.SymTabAST;
import com.puppycrawl.tools.checkstyle.checks.usage.transmogrify.SymTabASTFactory;
import com.puppycrawl.tools.checkstyle.checks.usage.transmogrify.SymbolTable;
import com.puppycrawl.tools.checkstyle.checks.usage.transmogrify.SymbolTableException;
import com.puppycrawl.tools.checkstyle.checks.usage.transmogrify.TableMaker;

/**
 * Manages AST trees and nodes. Capable of managing multiple parse trees, which
 * is useful for inter-file checks.
 * @author Rick Giles
 */
public final class ASTManager
{
    /** singleton */
    private static final ASTManager sInstance = new ASTManager();

    /** maps DetailASTs to SymTabASTs. */
    private Map mMap = new IdentityHashMap();
        
    /** root with subtrees for a set of files */
    private SymTabAST mCompleteTree = null;

    /** Map for parse trees, keyed on File name */
    private Map mTrees = new HashMap();

    /** Set of checks and their nodes to check */
    private Map mCheckNodes = new HashMap();

    /** prevent client creation */
    private ASTManager()
    {
    }
    
    /**
     * Returns the singleon ASTManager.
     * @return the singleon ASTManager.
     */
    public static ASTManager getInstance()
    {
        return sInstance;
    }

    /**
     * Add the parse tree for a file to the set of parse trees.
     * @param aFileName the name of the file.
     * @param aRoot the root of the AST.
     */
    public void addTree(String aFileName, AST aRoot)
    {
        mTrees.put(aFileName, aRoot);
    }

    /**
     * Builds the complete tree for all added parse trees.
     */
    private void buildTree()
        throws SymbolTableException
    {
        mCompleteTree = SymTabASTFactory.create(0, "AST Root");
        final Set keys = mTrees.keySet();
        final Iterator it = keys.iterator();
        while (it.hasNext()) {
            final String fileName = (String) it.next();
            final File file = new File(fileName);
            final AST rootAST = (AST) mTrees.get(fileName);
            addToCompleteTree(file, rootAST);
        }

        // Walk of the complete tree.
        // TODO: This is a hack. Find a better way.
        final SymbolTable table = new TableMaker(mCompleteTree).getTable();
    }

    /**
     * Adds a file and a DetailAST to the root SymTabAST tree. Normally, the
     * DetailAST will be the parse tree for the file.
     * @param aFile the file to add.
     * @param aAST the DetailAST to add.
    */
    private void addToCompleteTree(File aFile, AST aAST) {
        // add aFile to the root
        final SymTabAST fileNode =
            SymTabASTFactory.create(0, aFile.getAbsolutePath());
        fileNode.setFile(aFile);
        mCompleteTree.addChild(fileNode);
        fileNode.setParent(mCompleteTree);
        
        // add aAST to aFile
        final SymTabAST child = SymTabASTFactory.create(aAST);
        child.setFile(aFile);
        fileNode.addChild(child);
        child.setParent(fileNode);
        fileNode.finishDefinition(aFile, mCompleteTree);
    }

    /**
     * Registers a node for checking.
     * @param aCheck the check to apply.
     * @param aNode the node to check.
     */
    public void registerCheckNode(AbstractUsageCheck aCheck, AST aNode)
    {
        Set nodeSet = (Set) mCheckNodes.get(aCheck);
        if (nodeSet == null) {
            nodeSet = new HashSet();
            nodeSet.add(aNode);
            mCheckNodes.put(aCheck, nodeSet);
        }
        else {
            nodeSet.add(aNode);
        }
    }

    /**
     * Gets the nodes to check with a usage check.
     * @param aCheck the usage check.
     * @return the nodes to check with aCheck.
     * @throws SymbolTableException if there is an error.
     */
    public Set getCheckNodes(AbstractUsageCheck aCheck)
        throws SymbolTableException
    {
        // lazy initialization
        if (mCompleteTree == null) {
              buildTree();
        }
        Set result = (Set) mCheckNodes.get(aCheck);
        if (result == null) {
            result = new HashSet();
        }
        return result;
    }

    /**
     * Maps a DetailAST to its associated SymTabAST.
     * @param aDetailAST the DetailAST.
     * @param aSymTabAST the SymTabAST associated with aDetailAST.
     */
    public void put(DetailAST aDetailAST, SymTabAST aSymTabAST)
    {
        mMap.put(aDetailAST, aSymTabAST);
    }

    /**
     * Gets the SymTabAST associated with a DetailAST.
     * @param aAST the DetailAST.
     * @return the the SymTabAST associated with aAST.
     */
    public SymTabAST get(DetailAST aAST)
    {
        return (SymTabAST) mMap.get(aAST);
    }
    
    /**
     * Clears all associations from DetailsASTs to SymTabASTs.
     */
    public void clearDetailsMap()
    {
        mMap.clear();
    }

    /**
     * Determines whether the map from DetailsASTs to SymTabASTs is empty.
     * @return true if the map is empty.
     */
    public boolean isEmptyDetailsMap()
    {
        return mMap.isEmpty();
    }

    /**
     * Clears all managed elements.
     */
    public void clear()
    {
        mCheckNodes.clear();
        mCompleteTree = null;
        mMap .clear();
        mTrees.clear();
    }
}
