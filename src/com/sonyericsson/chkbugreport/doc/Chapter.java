package com.sonyericsson.chkbugreport.doc;

import com.sonyericsson.chkbugreport.ChapterParent;
import com.sonyericsson.chkbugreport.Module;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.Vector;

public class Chapter extends DocNode implements ChapterParent {

    private Vector<Chapter> mSubChapters = new Vector<Chapter>();
    private Chapter mParent = null;
    private String mName;
    private Renderer mRenderer;
    private Module mMod;
    private Anchor mAnchor;
    private int mId;
    private Header mHeader;
    private DocNode mInit;
    private Link mPopout;

    public Chapter(Module mod, String name) {
        mMod = mod;
        mName = name;
        mId = mMod.allocChapterId();
        mInit = new DocNode(this);
        mInit.add(mAnchor = new Anchor("ch" + mId));
        mPopout = new Link(mAnchor, null);
        mPopout.add(new Img("ic_pop_out.png"));
        mPopout.setTarget("_blank");
        mInit.add(new Block().addStyle("btn-pop-out").add(mPopout));
        mInit.add(mHeader = new Header(mName));
    }

    public void removePopout() {
        mInit.remove(mPopout.getParent());
    }

    public Module getModule() {
        return mMod;
    }

    public Anchor getAnchor() {
        return mAnchor;
    }

    public String getName() {
        return mName;
    }

    public void setName(String name) {
        mName = name;
        mHeader.setName(name);
    }

    @Override
    public void addChapter(Chapter ch) {
        mSubChapters.add(ch);
    }

    public void insertChapter(int pos, Chapter ch) {
        mSubChapters.insertElementAt(ch, pos);
    }

    @Override
    public int getChapterCount() {
        return mSubChapters.size();
    }

    @Override
    public Chapter getChapter(int idx) {
        return mSubChapters.get(idx);
    }

    @Override
    public Chapter getChapter(String name) {
        for (Chapter ch : mSubChapters) {
            if (name.equals(ch.getName())) {
                return ch;
            }
        }
        return null;
    }

    @Override
    public void prepare(Renderer r) {
        mRenderer = r.addLevel(this);

        if (isStandalone() && getChapterCount() > 0) {
            List list = new List(List.TYPE_UNORDERED);
            for (Chapter child : mSubChapters) {
                list.add(new Link(child.getAnchor(), child.getName()));
            }
            new Block(this).addStyle("box")
                .add("Jump to:")
                .add(list);
        }
        super.prepare(mRenderer);
        for (Chapter child : mSubChapters) {
            child.prepare(mRenderer);
        }
    }

    private boolean isStandalone() {
        return mRenderer.isStandalone();
    }

    @Override
    public void render(Renderer r) throws IOException {
        mMod.printOut(2, "Writing chapter: " + getFullName() + "...");
        mRenderer.begin();

        // This will render the own content of this chapter
        super.render(mRenderer);

        // This will render the subchapters
        for (Chapter child : mSubChapters) {
            child.render(mRenderer);
        }

        mRenderer.end();
    }

    public String getFullName() {
        if (mParent != null) {
            String ret = mParent.getFullName();
            if (ret != null) {
                return ret + "/" + getName();
            }
        }
        return getName();
    }

    @Override
    public boolean isEmpty() {
        // Note: there is a default child
        return mSubChapters.isEmpty() && getChildCount() <= 1;
    }

    public int getId() {
        return mId;
    }

    public void sort(Comparator<Chapter> comparator) {
        Collections.sort(mSubChapters, comparator);
    }

}
