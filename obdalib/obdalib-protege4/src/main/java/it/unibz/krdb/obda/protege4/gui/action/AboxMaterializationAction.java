package it.unibz.krdb.obda.protege4.gui.action;

import it.unibz.krdb.obda.gui.swing.utils.OBDAProgessMonitor;
import it.unibz.krdb.obda.gui.swing.utils.OBDAProgressListener;
import it.unibz.krdb.obda.model.OBDAModel;
import it.unibz.krdb.obda.model.impl.OBDAModelImpl;
import it.unibz.krdb.obda.owlapi.util.OBDA2OWLDataMaterializer;
import it.unibz.krdb.obda.protege4.core.OBDAModelManager;

import java.awt.Container;
import java.awt.event.ActionEvent;
import java.util.concurrent.CountDownLatch;

import javax.swing.JOptionPane;

import org.protege.editor.core.ui.action.ProtegeAction;
import org.protege.editor.owl.OWLEditorKit;
import org.protege.editor.owl.model.OWLModelManager;
import org.semanticweb.owl.model.OWLOntology;
import org.semanticweb.owl.model.OWLOntologyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/***
 * Action to create individuals into the currently open OWL Ontology using the
 * existing mappings from ALL datasources
 * 
 * @author Mariano Rodriguez Muro
 * 
 */
public class AboxMaterializationAction extends ProtegeAction {

	/**
	 * 
	 */
	private static final long	serialVersionUID	= -1211395039869926309L;

	private Logger				log					= LoggerFactory.getLogger(AboxMaterializationAction.class);

	private MaterializeAction action = null;
	
	@Override
	public void initialise() throws Exception {
		// TODO Auto-generated method stub

	}

	@Override
	public void dispose() throws Exception {
		// TODO Auto-generated method stub

	}

	@Override
	public void actionPerformed(ActionEvent arg0) {

		if (!(getEditorKit() instanceof OWLEditorKit) || !(getEditorKit().getModelManager() instanceof OWLModelManager))
			return;

		int response = JOptionPane
				.showConfirmDialog(
						this.getEditorKit().getWorkspace(),
						"This will use the mappings of the OWL-OBDA model \n to create a set of 'individual' assertions as specified \n by the mappings. \n\n This operation can take a long time and can require a lot of memory \n if the volume data retrieved by the mappings is high.",
						"Confirm", JOptionPane.OK_CANCEL_OPTION);
		if (response == JOptionPane.CANCEL_OPTION || response == JOptionPane.CLOSED_OPTION || response == JOptionPane.NO_OPTION)
			return;

		OWLEditorKit kit = (OWLEditorKit) this.getEditorKit();
		OWLModelManager mm = kit.getOWLModelManager();
		Container cont = this.getWorkspace().getRootPane().getParent();
		OBDAModel obdaapi = ((OBDAModelManager) kit.get(OBDAModelImpl.class.getName())).getActiveOBDAModel();

		try {
			OWLOntologyManager owlOntManager = mm.getOWLOntologyManager();
			OWLOntology owl_ont = mm.getActiveOntology();
			OBDA2OWLDataMaterializer mat = new OBDA2OWLDataMaterializer();
			action = new MaterializeAction(mat, obdaapi, owl_ont, owlOntManager);
			
			Thread th = new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						OBDAProgessMonitor monitor = new OBDAProgessMonitor();
						CountDownLatch latch = new CountDownLatch(1);
						action.setCountdownLatch(latch);
						monitor.addProgressListener(action);
						monitor.start();
						action.run();
						latch.await();
						monitor.stop();
					} catch (InterruptedException e) {
						log.error(e.getMessage(), e);
						JOptionPane.showMessageDialog(null, "ERROR: could not materialize abox.");
					}
				}
			});
			th.start();
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			JOptionPane.showMessageDialog(cont, "ERROR: could not create individuals. See the log for more informaiton.", "Error", JOptionPane.ERROR_MESSAGE);
			
		}

	}

	private class MaterializeAction implements OBDAProgressListener {

		private Thread				thread	= null;

		private CountDownLatch		latch	= null;

		private OBDA2OWLDataMaterializer	mat		= null;
		private OWLOntology			owl_ont	= null;
		private OWLOntologyManager	man		= null;
		OBDAModel				obdapi	= null;

		public MaterializeAction(OBDA2OWLDataMaterializer mat, OBDAModel obdaapi, OWLOntology owl_ont, OWLOntologyManager man) {
			this.obdapi = obdaapi;
			this.mat = mat;
			this.owl_ont = owl_ont;
			this.man = man;
		}

		public void setCountdownLatch(CountDownLatch cdl){
			latch = cdl;
		}
		
		public void run() {
			if(latch == null){
				try {
					throw new Exception("No CountDownLatch set");
				} catch (Exception e) {
					log.error(e.getMessage(), e);
					JOptionPane.showMessageDialog(null, "ERROR: could not materialize abox.");
					return;
				}
			}
			thread = new Thread() {
				public void run() {
					try {
						mat.materializeAbox(obdapi, man, owl_ont);						
						latch.countDown();
						Container cont = AboxMaterializationAction.this.getWorkspace().getRootPane().getParent();

						JOptionPane.showMessageDialog(cont, "Task completed", "Done", JOptionPane.INFORMATION_MESSAGE);
					} catch (Exception e) {
						latch.countDown();
						JOptionPane.showMessageDialog(null, "ERROR: could not materialize abox.");
					}
				}
			};
			thread.start();
		}

		@Override
		public void actionCanceled() {
			if (thread != null) {
				latch.countDown();
				thread.interrupt();
			}
		}

	}

}
