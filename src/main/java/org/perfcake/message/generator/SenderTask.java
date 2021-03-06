package org.perfcake.message.generator;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

import org.perfcake.PerfCakeConst;
import org.perfcake.message.Message;
import org.perfcake.message.MessageTemplate;
import org.perfcake.message.ReceivedMessage;
import org.perfcake.message.sender.MessageSender;
import org.perfcake.message.sender.MessageSenderManager;
import org.perfcake.reporting.ReportManager;
import org.perfcake.validation.ValidatorManager;

/**
 * <p>
 * The sender task is a runnable class that is executing a single task of sending the message(s) from the message store using instances of {@link MessageSender} provided by message sender manager (see {@link MessageSenderManager}), receiving the message sender's response and handling the reporting and response message validation.
 * </p>
 * <p>
 * It is used by the generators.
 * </p>
 * 
 * @author Pavel Macík <pavel.macik@gmail.com>
 */
class SenderTask implements Runnable {

   /**
    * Reference to a message sender manager that is providing the message senders.
    */
   private final MessageSenderManager senderManager;

   /**
    * Reference to a message store where the messages are taken from.
    */
   private final List<MessageTemplate> messageStore;

   /**
    * Reference to a counter of successfully executed iterations.
    */
   private final AtomicLong counter;

   /**
    * Indicates whether the message numbering is enabled or disabled.
    */
   private final boolean messageNumberingEnabled;

   /**
    * Indicates whether the system is in a state when it measures the performance.
    */
   private final boolean isMeasuring;

   /**
    * Reference to a report manager.
    */
   private final ReportManager reportManager;

   /**
    * Creates a new instance of SenderTask.
    * 
    * @param reportManager
    *           Reference to a report manager.
    * @param counter
    *           Reference to a counter of successfully executed iterations.
    * @param senderManager
    *           Reference to a message sender manager that is providing the message senders.
    * @param messageStore
    *           Reference to a message store where the messages are taken from.
    * @param messageNumberingEnabled
    *           Indicates whether the message numbering is enabled or disabled.
    * @param isMeasuring
    *           Indicates whether the system is in a state when it measures the performance.
    */
   public SenderTask(ReportManager reportManager, AtomicLong counter, MessageSenderManager senderManager, List<MessageTemplate> messageStore, boolean messageNumberingEnabled, boolean isMeasuring) {
      this.reportManager = reportManager;
      this.senderManager = senderManager;
      this.messageStore = messageStore;
      this.counter = counter;
      this.messageNumberingEnabled = messageNumberingEnabled;
      this.isMeasuring = isMeasuring;
   }

   /*
    * (non-Javadoc)
    * 
    * @see java.lang.Runnable#run()
    */
   @Override
   public void run() {
      Properties messageAttributes = new Properties();
      HashMap<String, String> messageHeaders = new HashMap<>();
      MessageSender sender = null;
      ReceivedMessage receivedMessage = null;
      try {
         Iterator<MessageTemplate> iterator = messageStore.iterator();

         // only set numbering to headers if it is enabled, later there is no change to
         // filter out the headers before sending
         String msgNumberStr = String.valueOf(counter.get());
         if (messageNumberingEnabled && isMeasuring) {
            messageHeaders.put(PerfCakeConst.MESSAGE_NUMBER_PROPERTY, msgNumberStr);
         }

         if (iterator.hasNext()) {
            while (iterator.hasNext()) {
               sender = senderManager.acquireSender();
               MessageTemplate messageToSend = iterator.next();
               Message currentMessage = messageToSend.getFilteredMessage(messageAttributes);
               long multiplicity = messageToSend.getMultiplicity();

               for (int i = 0; i < multiplicity; i++) {
                  receivedMessage = new ReceivedMessage(sender.send(currentMessage, messageHeaders), messageToSend);
                  if (ValidatorManager.isEnabled()) {
                     ValidatorManager.addToResultMessages(receivedMessage);
                  }
               }

               senderManager.releaseSender(sender); // !!! important !!!
               sender = null;
            }
         } else {
            sender = senderManager.acquireSender();
            receivedMessage = new ReceivedMessage(sender.send(null, messageHeaders), null);
            if (ValidatorManager.isEnabled()) {
               ValidatorManager.addToResultMessages(receivedMessage);
            }
            senderManager.releaseSender(sender); // !!! important !!!
            sender = null;
         }

         counter.incrementAndGet();
         reportManager.reportIteration();
      } catch (Exception e) {
         e.printStackTrace();
      } finally {
         if (sender != null) {
            senderManager.releaseSender(sender);
         }
      }
   }
}