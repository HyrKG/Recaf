package me.coley.recaf.ui.media;

import java.io.IOException;
import java.util.List;

/**
 * Audio-player that wraps multiple other implementations for wider support.
 *
 * @author Matt Coley
 */
public class CombinedPlayer extends AudioPlayer {
	private final List<AudioPlayer> delegates;
	private AudioPlayer currentPlayer;

	/**
	 * @param delegates
	 * 		Backing players.
	 */
	public CombinedPlayer(List<AudioPlayer> delegates) {
		this.delegates = delegates;
	}

	@Override
	public void play() {
		if (currentPlayer != null)
			currentPlayer.play();
	}

	@Override
	public void pause() {
		if (currentPlayer != null)
			currentPlayer.pause();
	}

	@Override
	public void stop() {
		if (currentPlayer != null)
			currentPlayer.stop();
	}

	@Override
	public double getMaxSeconds() {
		if (currentPlayer != null)
			return currentPlayer.getMaxSeconds();
		return super.getMaxSeconds();
	}

	@Override
	public double getCurrentSeconds() {
		if (currentPlayer != null)
			return currentPlayer.getCurrentSeconds();
		return super.getCurrentSeconds();
	}

	@Override
	public void setSpectrumListener(SpectrumListener listener) {
		super.setSpectrumListener(listener);
		// Also set listener for delegated players
		delegates.forEach(delegate -> delegate.setSpectrumListener(listener));
	}

	@Override
	public void load(String path) throws IOException {
		// Reset prior content
		stop();
		// Attempt to load content from delegates
		for (AudioPlayer player : delegates) {
			try {
				player.load(path);
				// Success, use ths player
				currentPlayer = player;
				return;
			} catch (IOException ignored) {
				// ignore to allow other delegated players to attempt loading
			}
		}
		throw new IOException("Failed to load audio file from path: " + path);
	}
}
