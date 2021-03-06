/*! ******************************************************************************
 *
 * Pentaho Data Integration
 *
 * Copyright (C) 2002-2018 by Hitachi Vantara : http://www.pentaho.com
 *
 *******************************************************************************
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/


package org.pentaho.di.trans.step.jms;

import io.reactivex.Observable;
import io.reactivex.ObservableOnSubscribe;
import org.pentaho.di.trans.streaming.common.BaseStreamStep;
import org.pentaho.di.trans.streaming.common.BlockingQueueStreamSource;

import javax.jms.JMSConsumer;
import javax.jms.JMSRuntimeException;
import javax.jms.Message;
import java.util.List;

import static com.google.common.collect.ImmutableList.of;
import static io.reactivex.schedulers.Schedulers.io;
import static java.util.Collections.singletonList;

public class JmsStreamSource extends BlockingQueueStreamSource<List<Object>> {

  private final JmsDelegate jmsDelegate;
  private JMSConsumer consumer;

  JmsStreamSource( BaseStreamStep streamStep, JmsDelegate jmsDelegate ) {
    super( streamStep );
    this.jmsDelegate = jmsDelegate;
  }

  @Override public void open() {
    consumer = jmsDelegate.getJmsContext().createConsumer( jmsDelegate.getDestination() );
    Observable.create( receiveLoop() )
      .subscribeOn( io() )
      .observeOn( io() )
      .doOnError( this::error ) // propogate the error
      .forEach( message -> acceptRows( singletonList( of( message, jmsDelegate.destinationName ) ) ) );
  }

  /**
   * Will receive messages from consumer.  If timeout is hit, consumer.receive(timeout)
   * will return null, and the observable will be completed.
   */
  private ObservableOnSubscribe<Object> receiveLoop() {
    return emitter -> {
      Message message;
      try {
        while ( ( message = consumer.receive( jmsDelegate.receiveTimeout ) ) != null ) {
          streamStep.logDebug( message.toString() );
          emitter.onNext( message.getBody( Object.class ) );
        }
      } catch ( JMSRuntimeException jmsException ) {
        emitter.onError( jmsException );
      }
      emitter.onComplete();
    };
  }

  @Override public void close() {
    super.close();
    if ( consumer != null ) {
      consumer.close();
    }
  }
}
