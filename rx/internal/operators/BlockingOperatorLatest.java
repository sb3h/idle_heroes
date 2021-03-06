package rx.internal.operators;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
import rx.Notification;
import rx.Observable;
import rx.Subscriber;
import rx.exceptions.Exceptions;

public final class BlockingOperatorLatest {

    static final class LatestObserverIterator<T> extends Subscriber<Notification<? extends T>> implements Iterator<T> {
        Notification<? extends T> iteratorNotification;
        final Semaphore notify = new Semaphore(0);
        final AtomicReference<Notification<? extends T>> value = new AtomicReference();

        LatestObserverIterator() {
        }

        public void onNext(Notification<? extends T> args) {
            if (this.value.getAndSet(args) == null) {
                this.notify.release();
            }
        }

        public void onError(Throwable e) {
        }

        public void onCompleted() {
        }

        public boolean hasNext() {
            if (this.iteratorNotification == null || !this.iteratorNotification.isOnError()) {
                if ((this.iteratorNotification == null || !this.iteratorNotification.isOnCompleted()) && this.iteratorNotification == null) {
                    try {
                        this.notify.acquire();
                        this.iteratorNotification = (Notification) this.value.getAndSet(null);
                        if (this.iteratorNotification.isOnError()) {
                            throw Exceptions.propagate(this.iteratorNotification.getThrowable());
                        }
                    } catch (InterruptedException ex) {
                        unsubscribe();
                        Thread.currentThread().interrupt();
                        this.iteratorNotification = Notification.createOnError(ex);
                        throw Exceptions.propagate(ex);
                    }
                }
                return !this.iteratorNotification.isOnCompleted();
            } else {
                throw Exceptions.propagate(this.iteratorNotification.getThrowable());
            }
        }

        public T next() {
            if (hasNext() && this.iteratorNotification.isOnNext()) {
                T v = this.iteratorNotification.getValue();
                this.iteratorNotification = null;
                return v;
            }
            throw new NoSuchElementException();
        }

        public void remove() {
            throw new UnsupportedOperationException("Read-only iterator.");
        }
    }

    private BlockingOperatorLatest() {
        throw new IllegalStateException("No instances!");
    }

    public static <T> Iterable<T> latest(final Observable<? extends T> source) {
        return new Iterable<T>() {
            public Iterator<T> iterator() {
                Subscriber lio = new LatestObserverIterator();
                source.materialize().subscribe(lio);
                return lio;
            }
        };
    }
}
