package com.github.mostroverkhov.r2.example.ui;

import com.github.mostroverkhov.r2.example.contract.model.AssemblyLinesResponse;
import de.vandermeer.asciitable.AsciiTable;

public class ClientContent implements Content {
  private final AssemblyLinesResponse resp;

  public ClientContent(AssemblyLinesResponse resp) {
    this.resp = resp;
  }

  @Override
  public String render() {
    StringBuilder sb = new StringBuilder();
    sb.append("CLIENT: Environment state received").append("\n");

    AsciiTable at = new AsciiTable();
    at.addRule();
    at.addRow("Temperature", "Humidity", "Particles");
    at.addRule();
    at.addRow(resp.getTemperature(), resp.getHumidity(), resp.getParticles());
    at.addRule();

    return sb.append(at.render(65)).toString();
  }
}
