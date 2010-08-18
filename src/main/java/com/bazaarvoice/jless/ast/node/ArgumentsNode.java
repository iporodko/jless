package com.bazaarvoice.jless.ast.node;

import com.bazaarvoice.jless.ast.visitor.NodeTraversalVisitor;

import java.util.ListIterator;

public class ArgumentsNode extends InternalNode {

    public ArgumentsNode() {
        super();
    }

    public ArgumentsNode(Node child) {
        super(child);
    }

    @Override
    public boolean accept(NodeTraversalVisitor visitor) {
        if (visitor.enter(this)) {
            ListIterator<Node> it = pushChildIterator();
            while (it.hasNext()) {
                Node child = it.next();
                if (!child.accept(visitor)) {
                    break;
                }
            }
            popChildIterator();
        }

        return visitor.visit(this);
    }
}