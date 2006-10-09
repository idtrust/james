package org.apache.james.mailboxmanager.torque.om.map;

import java.util.Date;
import java.math.BigDecimal;

import org.apache.torque.Torque;
import org.apache.torque.TorqueException;
import org.apache.torque.map.MapBuilder;
import org.apache.torque.map.DatabaseMap;
import org.apache.torque.map.TableMap;

/**
  *  This class was autogenerated by Torque on:
  *
  * [Tue Sep 19 10:06:28 CEST 2006]
  *
  */
public class MailboxRowMapBuilder implements MapBuilder
{
    /**
     * The name of this class
     */
    public static final String CLASS_NAME =
        "org.apache.james.mailboxmanager.torque.om.map.MailboxRowMapBuilder";

    /**
     * The database map.
     */
    private DatabaseMap dbMap = null;

    /**
     * Tells us if this DatabaseMapBuilder is built so that we
     * don't have to re-build it every time.
     *
     * @return true if this DatabaseMapBuilder is built
     */
    public boolean isBuilt()
    {
        return (dbMap != null);
    }

    /**
     * Gets the databasemap this map builder built.
     *
     * @return the databasemap
     */
    public DatabaseMap getDatabaseMap()
    {
        return this.dbMap;
    }

    /**
     * The doBuild() method builds the DatabaseMap
     *
     * @throws TorqueException
     */
    public void doBuild() throws TorqueException
    {
        dbMap = Torque.getDatabaseMap("mailboxmanager");

        dbMap.addTable("mailbox");
        TableMap tMap = dbMap.getTable("mailbox");

        tMap.setPrimaryKeyMethod(TableMap.NATIVE);

        tMap.setPrimaryKeyMethodInfo("mailbox");

              tMap.addPrimaryKey("mailbox.MAILBOX_ID", new Long(0) );
                        	          tMap.addColumn("mailbox.NAME", "", 255 );
                                        tMap.addColumn("mailbox.UID_VALIDITY", new Long(0) );
                                tMap.addColumn("mailbox.LAST_UID", new Long(0) );
                                tMap.addColumn("mailbox.MESSAGE_COUNT", new Integer(0) );
                                tMap.addColumn("mailbox.SIZE", new Long(0) );
                }
}
