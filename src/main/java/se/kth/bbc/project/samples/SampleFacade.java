package se.kth.bbc.project.samples;

import java.util.Date;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import se.kth.kthfsdashboard.user.AbstractFacade;

/**
 *
 * @author stig
 */
@Stateless
public class SampleFacade extends AbstractFacade<Sample> {

  @PersistenceContext(unitName = "kthfsPU")
  private EntityManager em;

  @Override
  protected EntityManager getEntityManager() {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  public SampleFacade() {
    super(Sample.class);
  }

  public Sample findById(String id) {
    TypedQuery<Sample> q = em.createNamedQuery(
            "Sample.findById",
            Sample.class);
    q.setParameter("id", id);
    try {
      return q.getSingleResult();
    } catch (NoResultException e) {
      return null;
    }
  }

  /*
   * TODO: this must be the most idiotic update function ever written... But:
   * Writing
   * em.merge(meta);
   * for some reason fails. The resulting queries insert project_id null in the
   * PROJECT_DESIGN table.
   * I have not yet figured out why...
   */
  public void update(Sample meta) {
    Sample old = findById(meta.getId());
    if (old != null) {
      em.remove(old);
      em.flush();
    }
    em.persist(meta);
  }

  public void persist(Sample s) {
    if (s.getSampledTime() == null) {
      s.setSampledTime(new Date());
    }
    em.persist(s);
  }

  /**
   * Check if a sample with this id already exists.
   * <p>
   * @param id
   * @return
   */
  public boolean existsSampleWithId(String id) {
    TypedQuery<Sample> q = em.createNamedQuery("Sample.findById", Sample.class);
    q.setParameter("id", id);
    return q.getResultList().size() > 0;
  }

}
