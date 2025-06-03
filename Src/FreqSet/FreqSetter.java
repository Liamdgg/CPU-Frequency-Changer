package FreqSet;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class FreqSetter extends JFrame {
	
	private JTextField freqField;
	private JButton setBtn;
	private JTextArea outptArea;
	private JLabel statusLbl;

	private static final long serialVersionUID = 1L;

	public FreqSetter() {
		
		setTitle("CPU Frequency Setter");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setSize(500, 400);
		setLocationRelativeTo(null);
		setLayout(new BorderLayout(10, 10));
		
		
		// input panel
		
		JPanel inputPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		inputPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
		
		freqField = new JTextField(10);
		setBtn = new JButton("Set Frequency");
		
		inputPanel.add(new JLabel("Enter desired CPU frequency(MHz): "));
		inputPanel.add(freqField);
		inputPanel.add(setBtn);
		
		// output 
		
		outptArea = new JTextArea();
		outptArea.setEditable(false);
		outptArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
		JScrollPane scrollPane = new JScrollPane(outptArea);
		scrollPane.setBorder(BorderFactory.createTitledBorder("Command output"));
		
		// status
		
		statusLbl = new JLabel("Ready, make sure to run as admin before.", SwingConstants.CENTER);
		statusLbl.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
		
		// add to frame
		
		add(inputPanel, BorderLayout.NORTH);
		add(scrollPane, BorderLayout.CENTER);
		add(statusLbl, BorderLayout.SOUTH);
		
		// btn action listener
		
		setBtn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				setCpuFrequency();
			}
		});
	}
	
	private void setCpuFrequency() {
		
		String freqStr = freqField.getText().trim();
		if (freqStr.isEmpty()) {
			JOptionPane.showMessageDialog(this, 
					"Please enter a freqency value.",
					"input error",
					JOptionPane.ERROR_MESSAGE);
			return;
		}
		
		int freq;
		try {
			freq = Integer.parseInt(freqStr);
			if (freq <= 0) {
				throw new NumberFormatException("Frequency must be positive");
			}
		} catch (NumberFormatException ex) {
			JOptionPane.showMessageDialog(this, 
					"Invalid frequency value. Enter a positive integer (e.g., 3500).",
					"Input Error",
					JOptionPane.ERROR_MESSAGE);
			return;
		}
		
		outptArea.setText("");
		statusLbl.setText("Processing...");
		setBtn.setEnabled(false);
		
		// swing worker
		
		SwingWorker<Boolean, String> worker = new SwingWorker<Boolean, String>() {

			@Override
			protected Boolean doInBackground() throws Exception {
				// TODO Auto-generated method stub
				boolean allSuccess = true;
				
				// command execute
				String[] commandsToFormat = {
						
						"powercfg /setACvalueindex scheme_current SUB_PROCESSOR PROCFREQMAX %d",
                        "powercfg /setACvalueindex scheme_current SUB_PROCESSOR PROCFREQMAX1 %d",
                        "powercfg /setDCvalueindex scheme_current SUB_PROCESSOR PROCFREQMAX %d",
                        "powercfg /setDCvalueindex scheme_current SUB_PROCESSOR PROCFREQMAX1 %d",
                        "powercfg /setactive scheme_current"
						
				};
				
				for (String cmdFormat : commandsToFormat) {
					String command;
					if (cmdFormat.contains("%d")) {
						command = String.format(cmdFormat, freq);
					} else {
						command = cmdFormat;
					}
					
					publish("Executing: "+ command + "\n");
					CommandResult result = executePowerShellCommand(command);
					publish(result.output + "\n");
					
					if (!result.success) {
						allSuccess = false;
						publish("ERROR: Command failed with exit code "+ result.exitCode + "\n");
					}
				}
				return allSuccess;
			}
			
			@Override
			protected void process(java.util.List<String> chunks) {
				for (String text : chunks) {
					outptArea.append(text);
				}
			}
			
			@Override
			protected void done() {
				try {
					boolean success = get();
					if (success) {
						statusLbl.setText("CPU frequency applied successfully");
						JOptionPane.showMessageDialog(FreqSetter.this, 
								"CPU frequency applied successfully to "+ freq + "MHz",
								"Success",
								JOptionPane.INFORMATION_MESSAGE);
					} else {
						statusLbl.setText("One or more commands failed. Check output");
						JOptionPane.showMessageDialog(FreqSetter.this, 
								"Error applying new CPU frequency. Check output for details.\n" + "Ensure you are running this as admin.",
								"Error",
								JOptionPane.ERROR_MESSAGE);
					}
				} catch (Exception ex) {
					statusLbl.setText("An error occured during execution.");
					outptArea.append("Exception: "+ ex.getMessage() + "\n");
					ex.printStackTrace();
					JOptionPane.showMessageDialog(FreqSetter.this, 
							"An unexpected error occured: "+ ex.getMessage(),
							"Execution error",
							JOptionPane.ERROR_MESSAGE);
				} finally {
					setBtn.setEnabled(true);
				}
			}
			
		};
		
		worker.execute();
		
	}
	
	private static class CommandResult {
		boolean success;
		int exitCode;
		String output;
		
		CommandResult(boolean success, int exitCode, String output) {
			this.success = success;
			this.exitCode = exitCode;
			this.output = output;
		}
		
	}
	
	private CommandResult executePowerShellCommand(String powerCfgCommand) {
		StringBuilder output = new StringBuilder();
		int exitCode = -1;
		boolean success = false;
		
		try {
			
			ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-Command", powerCfgCommand);
			pb.redirectErrorStream(true);
			
			Process process = pb.start();
			
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
				String line;
				while ((line = reader.readLine()) != null) {
					output.append(line).append(System.lineSeparator());
				}
			}
			
			exitCode = process.waitFor();
			success = (exitCode == 0);
			
		} catch (IOException | InterruptedException e ) {
			output.append("Exception during command execution: ").append(e.getMessage()).append(System.lineSeparator());
			e.printStackTrace();
			success = false;
		}
		return new CommandResult(success, exitCode, output.toString());
	}
	
	public static void main(String[] args) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				try {
					UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
				} catch (Exception e ) {
					System.err.println("Couldnt set system look and feel: "+ e.getMessage());
				}
				new FreqSetter().setVisible(true);
			}
		}); 
	}	
}
