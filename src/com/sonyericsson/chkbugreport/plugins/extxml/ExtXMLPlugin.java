package com.sonyericsson.chkbugreport.plugins.extxml;

import com.sonyericsson.chkbugreport.Module;
import com.sonyericsson.chkbugreport.Plugin;
import com.sonyericsson.chkbugreport.doc.Chapter;
import com.sonyericsson.chkbugreport.doc.SimpleText;
import com.sonyericsson.chkbugreport.util.XMLNode;

/**
 * This plugin parses and "executes" and xml file.
 * This makes it possible to create plugins which parses the log easily,
 * without needing to recompile the application.
 */
public class ExtXMLPlugin extends Plugin {

    private XMLNode mXml;

    public ExtXMLPlugin(XMLNode xml) {
        mXml = xml;
        if (!xml.getName().equals("plugin")) {
            throw new RuntimeException("Invalid root tag");
        }
    }

    @Override
    public int getPrio() {
        // Let them run these at the end, when the rest of the data is parsed
        return 99;
    }

    @Override
    public void reset() {
        // NOP
    }

    @Override
    public void load(Module mod) {
        // Execute the "load" tag
        // TODO: not implemented yet
    }

    @Override
    public void generate(Module mod) {
        // Find the generate tag
        XMLNode gen = mXml.getChild("generate");
        if (gen == null) {
            // <generate> tag is missing, do nothing.
            return;
        }

        // The generate tag MUST contain chapter tags
        for (XMLNode chTag : gen) {
            String tag = chTag.getName();
            if (tag == null) continue;
            if (!"chapter".equals(tag)) {
                mod.printErr(4, "A non-chapter tag is found in <generate>, ignoreing it: " + chTag.getName());
                continue;
            }
            Chapter ch = mod.findOrCreateChapter(chTag.getAttr("name"));
            // Now execute each child tag
            for (XMLNode code : chTag) {
                exec(mod, ch, code);
            }
        }
    }

    private void exec(Module mod, Chapter ch, XMLNode code) {
        String type = code.getName();
        if (type == null) {
            // NOP
        } else if ("text".equals(type)) {
            // This is trivial
            if (code.getChildCount() == 1) {
                String text = code.getChild(0).getAttr("text");
                ch.add(new SimpleText(text));
            }
        } else if ("logchart".equals(type)) {
            // Create a chart based on the log is a bit more complex, so let's delegate it
            new LogChart(mod, ch, code).exec();
        } else {
            mod.printErr(4, "Unknown code tag in logchart: " + code.getName());
        }

    }

}
