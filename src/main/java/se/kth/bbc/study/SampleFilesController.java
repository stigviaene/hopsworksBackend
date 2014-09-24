/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package se.kth.bbc.study;

import java.util.ArrayList;
import java.util.List;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import javax.persistence.TypedQuery;

/**
 *
 * @author stig
 */
@Stateless
public class SampleFilesController {
    @PersistenceContext(unitName = "kthfsPU")
    private EntityManager em;

    protected EntityManager getEntityManager() {
        return em;
    }

    public SampleFilesController() {

    }

    public List<SampleFiles> findAllById(String id){
        TypedQuery<SampleFiles> query = em.createNamedQuery("SampleFiles.findById", SampleFiles.class).setParameter("id", id);
        return query.getResultList();
    }
    
    public List<SampleFiles> findAllByIdType(String id, String type){
        Query query = em.createNativeQuery("SELECT * FROM SampleFiles WHERE id LIKE ? AND file_type LIKE ?",SampleFiles.class);
        query.setParameter(1, id);
        query.setParameter(2, type);
        return query.getResultList();
    }
    
    public List<String> findAllExtensionsForSample(String sampleId){
        Query query = em.createNativeQuery("SELECT DISTINCT file_type FROM SampleFiles WHERE id LIKE ?");
        query.setParameter(1, sampleId);
        List<Object> res = query.getResultList();
        List<String> stringRes = new ArrayList<>(res.size());
        for(Object o: res){
            stringRes.add((String)o);
        }
        return stringRes;
    }
    
    
    public void persistSampleFiles(SampleFiles sampleFiles){
        em.persist(sampleFiles);
    }

    public SampleFiles findByPrimaryKey(String id, String filename) {
        return em.find(SampleFiles.class, new SampleFiles(new SampleFilesPK(id, filename)).getSampleFilesPK());
    }

    public boolean checkForExistingSampleFiles(String id, String filename) {
        SampleFiles checkedFile = findByPrimaryKey(id, filename);
        if (checkedFile != null) {
            return true;
        } else {
            return false;
        }
    }
    
    public void update(String id, String filename){
        SampleFiles sf = findByPrimaryKey(id, filename);
        if(sf != null) {
            sf.setStatus(SampleFileStatus.AVAILABLE.getFileStatus());
            em.merge(sf);
        }
    }
    
}