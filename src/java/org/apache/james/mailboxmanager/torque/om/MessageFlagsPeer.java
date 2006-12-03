package org.apache.james.mailboxmanager.torque.om;

import java.util.List;

import javax.mail.Flags;

import org.apache.torque.TorqueException;
import org.apache.torque.util.Criteria;

/**
 * The skeleton for this class was autogenerated by Torque on:
 *
 * [Wed Sep 06 19:48:03 CEST 2006]
 *
 *  You should add additional methods to this class to meet the
 *  application requirements.  This class will only be generated as
 *  long as it does not already exist in the output directory.
 */
public class MessageFlagsPeer
    extends org.apache.james.mailboxmanager.torque.om.BaseMessageFlagsPeer
{
    
    /**
     * 
     */
    private static final long serialVersionUID = 4709341310937090513L;

    public static void addFlagsToCriteria(Flags flags,boolean value,Criteria c) {
        if (flags.contains(Flags.Flag.ANSWERED)) {
            c.add(ANSWERED,value);
        }
        if (flags.contains(Flags.Flag.DELETED)) {
            c.add(DELETED,value);
        }
        if (flags.contains(Flags.Flag.DRAFT)) {
            c.add(DRAFT,value);
        }
        if (flags.contains(Flags.Flag.FLAGGED)) {
            c.add(FLAGGED,value);
        }
        if (flags.contains(Flags.Flag.RECENT)) {
            c.add(RECENT,value);
        }
        if (flags.contains(Flags.Flag.SEEN)) {
            c.add(SEEN,value);
        }
    }
    
    public static List doSelectJoinMessageRow(Criteria criteria) throws TorqueException {
       return BaseMessageFlagsPeer.doSelectJoinMessageRow(criteria);
    }
}
