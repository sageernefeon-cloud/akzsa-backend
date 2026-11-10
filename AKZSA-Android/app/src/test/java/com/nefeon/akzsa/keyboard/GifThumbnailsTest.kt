package com.nefeon.akzsa.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class GifThumbnailsTest {
    @Test fun smallSourceIsNotShrunk() = assertEquals(1, GifThumbnails.sampleSize(100, 100))
    @Test fun sourceJustUnderTwiceTargetKeepsFullSize() = assertEquals(1, GifThumbnails.sampleSize(199, 100))
    @Test fun doubleTargetHalvesOnce() = assertEquals(2, GifThumbnails.sampleSize(200, 100))
    @Test fun neverShrinksBelowTarget() = assertEquals(4, GifThumbnails.sampleSize(450, 100))
    @Test fun badTargetFallsBackToOne() = assertEquals(1, GifThumbnails.sampleSize(500, 0))
}
